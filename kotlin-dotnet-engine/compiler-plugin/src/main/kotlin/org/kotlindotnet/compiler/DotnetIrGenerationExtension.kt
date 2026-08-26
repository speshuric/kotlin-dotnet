package org.kotlindotnet.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dump
import java.io.File
import org.kotlindotnet.compiler.util.Log

/**
 * Точечный вход плагина: перехватывает IR модуля после фронтенда (K2).
 *
 * - IR-dump: `ir-dump-<moduleFragment.name>.txt` в [outputDir]
 *   (best-effort, через [Log.warn] при неудаче).
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
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        outputDir.mkdirs()

        // Дамп IR — отладочный best-effort вывод (один файл на модуль).
        // Имя содержит имя модуля, чтобы не перетираться между модулями.
        // runCatching тут уместен: dump — не часть контракта эмиссии.
        val dumpName = "ir-dump-${moduleFragment.name}.txt"
        runCatching {
            val dumpFile = File(outputDir, dumpName)
            dumpFile.writeText(moduleFragment.dump())
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
            try {
                val emitter = org.kotlindotnet.compiler.pe.PeIlEmitter(
                    isDebug = config == "debug",
                    sourceFile = File(irFile.fileEntry.name).absolutePath,
                )
                val visitor = DotnetIrVisitor(emitter)
                irFile.accept(visitor, null)
                emitter.writeAssemblyTo(outFile, outputKind == "exe")
                // Sidecar portable PDB next to the assembly (both configs).
                val pdbFile = File(outputDir, "$asmName.pdb")
                emitter.writePortablePdbTo(pdbFile)
                Log.info("PDB: ${pdbFile.absolutePath}")
                Log.info("PE ($outputKind): ${outFile.absolutePath}")
            } catch (e: Throwable) {
                outFile.delete()
                throw IllegalStateException(
                    "PE emission failed for '${irFile.fileEntry.name}' → ${outFile.name}: ${e.message}",
                    e,
                )
            }
        }
    }
}
