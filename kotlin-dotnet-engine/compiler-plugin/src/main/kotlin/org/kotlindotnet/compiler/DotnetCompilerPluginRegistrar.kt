package org.kotlindotnet.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File

/**
 * The plugin registrar for the Kotlin compiler (K2).
 *
 * Replaces the deprecated (a deprecation ERROR since 2.4) `ComponentRegistrar`.
 * Discovered by the compiler via ServiceLoader:
 * `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`.
 *
 * CLI options are handled by a separate component —
 * [DotnetCommandLineProcessor] (discovered through
 * `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor`).
 */
class DotnetCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "kotlin.dotnet"

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // output.dir — from the CLI (-P plugin:kotlin.dotnet:output.dir=...);
        // the default `build/` keeps compatibility with the earlier behavior.
        // The plugin does not hardcode `build/` into paths: this fallback is the
        // only place. The key is taken from the companion object of
        // [DotnetCommandLineProcessor]: `CompilerConfigurationKey.create` builds a
        // `Key` with identity `equals`, so the same key instance is needed both in
        // `processOption` and here (see ADR 0007).
        val outputDir = configuration.get(DotnetCommandLineProcessor.outputDirKey)
            ?.let(::File)
            ?: File("build")
        val outputKind = configuration.get(DotnetCommandLineProcessor.outputKindKey) ?: "exe"
        val config = configuration.get(DotnetCommandLineProcessor.configKey) ?: "release"
        // Stdlib coverage strictness; value is validated at option parse
        // time, so the fallback here only normalizes absence -> lenient.
        val stdlibMode = StdlibMode.fromCli(
            configuration.get(DotnetCommandLineProcessor.stdlibModeKey) ?: "lenient",
        )
        IrGenerationExtension.registerExtension(
            DotnetIrGenerationExtension(outputDir, outputKind, config, stdlibMode)
        )
    }
}
