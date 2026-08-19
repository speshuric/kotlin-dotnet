package org.kotlindotnet.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File

/**
 * Регистратор плагина для компилятора Kotlin (K2).
 *
 * Заменяет устаревший (deprecated ERROR в 2.4) `ComponentRegistrar`.
 * Обнаруживается компилятором через ServiceLoader:
 * `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`.
 *
 * CLI-опции обрабатываются отдельным компонентом —
 * [DotnetCommandLineProcessor] (обнаруживается через
 * `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor`).
 */
class DotnetCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "kotlin.dotnet"

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // output.dir — из CLI (-P plugin:kotlin.dotnet:output.dir=...),
        // дефолт `build/` — для совместимости с предыдущим поведением.
        // Плагин не хардкодит `build/` в путях: только этот fallback.
        val outputDir = configuration.get(DotnetCommandLineProcessor().outputDirKey)
            ?.let(::File)
            ?: File("build")
        IrGenerationExtension.registerExtension(DotnetIrGenerationExtension(outputDir))
    }
}
