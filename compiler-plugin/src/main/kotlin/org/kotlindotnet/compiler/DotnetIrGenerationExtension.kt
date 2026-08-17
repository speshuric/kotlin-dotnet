package org.kotlindotnet.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dump
import java.io.File

/**
 * Точечный вход плагина: перехватывает IR модуля после фронтенда (K2).
 *
 * - Phase 4: дамп IR в build/ir-dump.txt (для инспекции).
 * - Phase 5: обход IR → генерация IL-текста → build/<File>.il.
 */
class DotnetIrGenerationExtension : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // Дамп IR — оставлен для отладки (в .gitignored build/).
        runCatching {
            val dumpFile = File("build/ir-dump.txt")
            dumpFile.parentFile.mkdirs()
            dumpFile.writeText(moduleFragment.dump())
            System.err.println("[kotlin-dotnet] IR dump: ${dumpFile.absolutePath}")
        }.onFailure { e ->
            System.err.println("[kotlin-dotnet] IR dump failed: ${e.message}")
        }

        // IL-генерация.
        val buildDir = File("build")
        buildDir.mkdirs()
        for (irFile in moduleFragment.files) {
            val emitter = IlEmitter()
            val visitor = DotnetIrVisitor(emitter)
            irFile.accept(visitor, null)

            val asmName = NameMapper.assemblyName(irFile)
            val ilFile = File(buildDir, "$asmName.il")
            ilFile.writeText(emitter.text())
            System.err.println("[kotlin-dotnet] IL: ${ilFile.absolutePath}")
        }
    }
}
