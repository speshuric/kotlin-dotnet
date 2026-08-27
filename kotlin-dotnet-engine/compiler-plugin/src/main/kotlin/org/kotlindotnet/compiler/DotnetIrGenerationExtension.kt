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
 * Точечный вход плагина: перехватывает IR модуля после фронтенда (K2).
 *
 * - IR-dump: `ir-dump-<moduleFragment.name>.txt` в [outputDir]
 *   (best-effort, через [Log.warn] при неудаче); в конец дампа дописывается
 *   сводка распознанных плагином аннотаций на декларациях и файловых
 *   (@file:) аннотаций — см. [annotationDump].
 * - PE-генерация: `outputDir/<asmName>.<kind>`. Ошибка эмиссии — не silent:
 *   бросает `IllegalStateException` с контекстом (имя файла), что
 *   фейлит компиляцию (см. A-02, ADR 0007).
 *
 * Директория вывода задаётся через CLI-опцию `output.dir` (см.
 * [DotnetCommandLineProcessor], ADR 0007). Плагин не хардкодит `build/`
 * в путях — `outputDir` приходит через конструктор.
 */
class DotnetIrGenerationExtension(
    private val outputDir: File,
    private val outputKind: String = "exe",
    private val config: String = "release",
    private val stdlibMode: StdlibMode = StdlibMode.LENIENT,
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        outputDir.mkdirs()

        // Дамп IR — отладочный best-effort вывод (один файл на модуль).
        // Имя содержит имя модуля, чтобы не перетираться между модулями.
        // runCatching тут уместен: dump — не часть контракта эмиссии.
        val dumpName = "ir-dump-${moduleFragment.name}.txt"
        runCatching {
            val dumpFile = File(outputDir, dumpName)
            dumpFile.writeText(moduleFragment.dump() + "\n" + annotationDump(moduleFragment))
            Log.info("IR dump: ${dumpFile.absolutePath}")
        }.onFailure { e ->
            Log.warn("IR dump failed ($dumpName): ${e.message}")
        }

        // Генерация — НЕ best-effort: ошибка эмиссии летит наружу и
        // фейлит компиляцию. visitor бросает TODO на неподдержанных узлах
        // — это нужное поведение (fail-fast, не silent empty output).
        for (irFile in moduleFragment.files) {
            val asmName = NameMapper.assemblyName(irFile)

            // ADR 0010: прямая генерация PE через dotnetutils.
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

                // Stdlib coverage policy (stdlib strategy Р3): applied AFTER
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
     * Дополняет текстовый dump сводкой аннотаций, которые плагин умеет
     * читать (см. [StdlibAnnotations]): обычные аннотации на декларациях
     * и файловые (@file:) — на [org.jetbrains.kotlin.ir.declarations.IrFile]
     * (он тоже IrAnnotationContainer). Секция предназначена для отладки
     * чтения аннотаций без отладчика; формат строк стабилен:
     *   annotations: <куда> <fqname>[(<значение>)]
     *
     * Файловая секция печатается первой: файловые таргеты применяются ко
     * всему модулю, их место — до деклараций.
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
 * Стабильное имя узла для строк дампа: IR-реализации носят суффикс Impl
 * (IrClassImpl/IrFunctionImpl), который не является контрактом; в дампе
 * оставляем семантическое имя, чтобы dump-grep-шаблоны тестов были
 * устойчивы к внутреннему неймингу.
 */
private fun IrDeclaration.dumpKindHint(): String =
    (this::class.simpleName ?: "decl").removeSuffix("Impl")
