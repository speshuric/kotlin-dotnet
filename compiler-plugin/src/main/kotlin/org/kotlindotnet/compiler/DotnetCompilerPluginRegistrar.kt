package org.kotlindotnet.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Регистратор плагина для компилятора Kotlin (K2).
 *
 * Заменяет устаревший (deprecated ERROR в 2.4) `ComponentRegistrar`.
 * Обнаруживается компилятором через ServiceLoader:
 * `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`.
 */
class DotnetCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "kotlin.dotnet"

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(DotnetIrGenerationExtension())
    }
}
