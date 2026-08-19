package org.kotlindotnet.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dump
import java.io.File
import org.kotlindotnet.compiler.il.IlEmitter
import org.kotlindotnet.compiler.il.TextIlEmitter
import org.kotlindotnet.compiler.util.Log

/**
 * Точечный вход плагина: перехватывает IR модуля после фронтенда (K2).
 *
 * - IR-dump: `ir-dump-<moduleFragment.name>.txt` в [outputDir]
 *   (best-effort, через [Log.warn] при неудаче).
 * - IL-генерация: `outputDir/<asmName>.il`. Ошибка эмиссии — не silent:
 *   бросает `IllegalStateException` с контекстом (имя файла), что
 *   фейлит компиляцию (см. A-02, ADR 0007).
 *
 * Директория вывода задаётся через CLI-опцию `output.dir` (см.
 * [DotnetCommandLineProcessor], ADR 0007). Плагин не хардкодит `build/`
 * в путях — `outputDir` приходит через конструктор.
 */
class DotnetIrGenerationExtension(
    private val outputDir: File,
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

        // IL-генерация — НЕ best-effort: ошибка эмиссии летит наружу и
        // фейлит компиляцию. visitor бросает TODO на неподдержанных узлах
        // — это нужное поведение (fail-fast, не silent empty .il).
        for (irFile in moduleFragment.files) {
            val asmName = NameMapper.assemblyName(irFile)
            val ilFile = File(outputDir, "$asmName.il")
            try {
                val emitter: IlEmitter = TextIlEmitter()
                val visitor = DotnetIrVisitor(emitter)
                irFile.accept(visitor, null)
                ilFile.writeText(emitter.text())
                Log.info("IL: ${ilFile.absolutePath}")
            } catch (e: Throwable) {
                // Удаляем потенциально пустой/битый .il, чтобы не вводить
                // в заблуждение последующие шаги (ilasm).
                ilFile.delete()
                throw IllegalStateException(
                    "IL emission failed for '${irFile.fileEntry.name}' → ${ilFile.name}: ${e.message}",
                    e,
                )
            }
        }
    }
}
