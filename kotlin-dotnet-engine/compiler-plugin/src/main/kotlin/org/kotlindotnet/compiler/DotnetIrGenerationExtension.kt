package org.kotlindotnet.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dump
import java.io.File
import org.kotlindotnet.compiler.util.Log

/**
 * The plugin's entry point: intercepts the module IR after the frontend (K2).
 *
 * - IR dump: `ir-dump-<moduleFragment.name>.txt` in [outputDir]
 *   (best-effort, via [Log.warn] on failure); the end of the dump gets a
 *   summary of the annotations the plugin recognizes on declarations and on
 *   file-level (@file:) annotations — see [annotationDump].
 * - PE generation: `outputDir/<asmName>.<kind>`. An emission error is not
 *   silent: it throws an `IllegalStateException` with context (the file name),
 *   which fails the compilation (see A-02, ADR 0007).
 *
 * The output directory is set through the CLI option `output.dir` (see
 * [DotnetCommandLineProcessor], ADR 0007). The plugin does not hardcode
 * `build/` in paths — `outputDir` arrives via the constructor.
 */
class DotnetIrGenerationExtension(
    private val outputDir: File,
    private val outputKind: String = "exe",
    private val config: String = "release",
    private val stdlibMode: StdlibMode = StdlibMode.LENIENT,
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        outputDir.mkdirs()

        // The IR dump is a debug-only best-effort output (one file per module).
        // The name embeds the module name so modules do not overwrite each other.
        // runCatching is appropriate here: the dump is not part of the emission contract.
        val dumpName = "ir-dump-${moduleFragment.name}.txt"
        runCatching {
            val dumpFile = File(outputDir, dumpName)
            dumpFile.writeText(moduleFragment.dump() + "\n" + annotationDump(moduleFragment))
            Log.info("IR dump: ${dumpFile.absolutePath}")
        }.onFailure { e ->
            Log.warn("IR dump failed ($dumpName): ${e.message}")
        }

        // Generation is NOT best-effort: an emission error propagates outward
        // and fails the compilation. The visitor throws TODO on unsupported nodes —
        // that is the intended behavior (fail-fast, not a silent empty output).
        for (irFile in moduleFragment.files) {
            val asmName = NameMapper.assemblyName(irFile)

            // ADR 0010: generate the PE directly via dotnetutils.
            val outFile = File(outputDir, "$asmName.$outputKind")
            // Collector lives outside the try: the strict failure path in
            // catch reads what the visitor managed to tally before aborting.
            val uncovered = UncoveredStdlibCollector()
            try {
                val emitter = org.kotlindotnet.compiler.pe.PeIlEmitter(
                    isDebug = config == "debug",
                    sourceFile = File(irFile.fileEntry.name).absolutePath,
                )
                val visitor = DotnetIrVisitor(emitter, uncovered)
                irFile.accept(visitor, null)
                emitter.writeAssemblyTo(outFile, outputKind == "exe")
                // Sidecar portable PDB next to the assembly (both configs).
                val pdbFile = File(outputDir, "$asmName.pdb")
                emitter.writePortablePdbTo(pdbFile)
                Log.info("PDB: ${pdbFile.absolutePath}")
                Log.info("PE ($outputKind): ${outFile.absolutePath}")

                // Stdlib coverage policy (stdlib strategy R3): applied AFTER
                // successful emission so strict failures list everything at
                // once; the artifact is already written but compilation will
                // fail below - delete it to avoid a stale binary.
                when {
                    uncovered.isEmpty -> Unit
                    stdlibMode == StdlibMode.STRICT -> {
                        outFile.delete()
                        File(outputDir, "$asmName.pdb").delete()
                        uncovered.failStrict(irFile.fileEntry.name)
                    }
                    else -> uncovered.reportLenient(File(irFile.fileEntry.name).name)
                }
            } catch (e: Throwable) {
                outFile.delete()
                // Strict mode: prepend the coverage summary so the user sees
                // every uncovered symbol that reached the funnel, not only
                // the first one that aborted emission.
                val strictSuffix =
                    if (stdlibMode == StdlibMode.STRICT && !uncovered.isEmpty) {
                        "\n" + uncovered.summaryLines().joinToString("\n  ", prefix = "Uncovered kotlin.* symbols:\n  ")
                    } else ""
                throw IllegalStateException(
                    "PE emission failed for '${irFile.fileEntry.name}' → ${outFile.name}: ${e.message}$strictSuffix",
                    e,
                )
            }
        }
    }

    /**
     * Appends to the textual dump a summary of the annotations the plugin can
     * read (see [StdlibAnnotations]): regular annotations on declarations and
     * file-level (@file:) ones on [org.jetbrains.kotlin.ir.declarations.IrFile]
     * (it is an IrAnnotationContainer too). This section exists to debug
     * annotation reading without a debugger; the line format is stable:
     *   annotations: <where> <fqname>[(<value>)]
     *
     * The file-level section is printed first: file targets apply to the whole
     * module and belong before the declarations.
     */
    private fun annotationDump(moduleFragment: org.jetbrains.kotlin.ir.declarations.IrModuleFragment): String =
        buildString {
            appendLine("=== recognized-annotations ===")
            for (irFile in moduleFragment.files) {
                for (line in AnnotationReader.summarize(irFile)) {
                    appendLine("annotations: file ${irFile.fileEntry.name} $line")
                }
                for (declaration in irFile.declarations) {
                    dumpDeclarationAnnotations(declaration, declaration.dumpKindHint())
                }
            }
        }

    private fun StringBuilder.dumpDeclarationAnnotations(declaration: IrDeclaration, kindHint: String) {
        if (declaration !is IrAnnotationContainer) return
        for (line in AnnotationReader.summarize(declaration)) {
            appendLine("annotations: $kindHint $line")
        }
        // Class members carry most user-facing annotation targets; recurse
        // one level so functions/properties inside classes are visible
        // without a debugger. Properties keep annotations on the property
        // node and on their accessors; accessors are IrSimpleFunction, they
        // arrive via the same recursion below.
        if (declaration is IrClass) {
            for (member in declaration.declarations) {
                dumpDeclarationAnnotations(member, member.dumpKindHint())
            }
        }
    }
}

/**
 * A stable node name for dump lines: IR implementations carry an Impl suffix
 * (IrClassImpl/IrFunctionImpl) that is not part of any contract; the dump keeps
 * the semantic name so test dump-grep patterns stay robust against internal
 * naming changes.
 */
private fun IrDeclaration.dumpKindHint(): String =
    (this::class.simpleName ?: "decl").removeSuffix("Impl")
