package org.kotlindotnet.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

/**
 * Handles the CLI options of the `kotlin.dotnet` plugin (passed via
 * `kotlinc -Xplugin=... -P plugin:kotlin.dotnet:<option>=<value>`).
 *
 * Options (see ADR 0007, ADR 0010):
 * - `output.dir` — the directory where the plugin writes artifacts
 *   (IR dump, PE files). Default: `build/` (set in
 *   [DotnetIrGenerationExtension], not here).
 * - `output.kind` — assembly kind: `exe` | `dll`.
 * - `config` — build mode: `debug` | `release`. Debug adds an
 *   assembly-level DebuggableAttribute; release goes without it
 *   (like csc without /debug).
 * - `stdlib.mode` — stdlib coverage strictness: `lenient` (the default;
 *   warnings about uncovered kotlin.* calls) | `strict`
 *   (a compile error with a summary list; see ADR 0014).
 *
 * Principle: the plugin does not hardcode `build/` — the path arrives via
 * [CompilerConfiguration]. One option, one concern (A-02).
 */
class DotnetCommandLineProcessor : CommandLineProcessor {

    override val pluginId: String = "kotlin.dotnet"

    /**
     * Configuration key: the plugin's output directory.
     * Stored as a `String` (not a `File`) — the plugin builds the `File` itself
     * at the point of use. The default (`build/`) also lives at the point
     * of use, so that the registrar/processor know nothing about it.
     *
     * **Singleton (companion object):** [CompilerConfigurationKey.create] creates a
     * [com.intellij.openapi.util.Key] internally, whose `equals` is an identity
     * comparison (confirmed against the bytecode of `Key.create`/`Key.equals`).
     * If the key were an instance `val`, then `DotnetCompilerPluginRegistrar`,
     * creating a `DotnetCommandLineProcessor()` to access the key, would get
     * a *different* `Key` → `configuration.get(...)` returns null
     * → fallback to `File("build")`. Keeping the key in the companion guarantees
     * the same `Key` object in both `processOption` and `registerExtensions`.
     * See ADR 0007; reference: allopen's `AllOpenConfigurationKeys` (object).
     */
    internal val outputDirKey: CompilerConfigurationKey<String> =
        Companion.outputDirKey

    internal val outputKindKey: CompilerConfigurationKey<String> =
        Companion.outputKindKey

    internal val configKey: CompilerConfigurationKey<String> =
        Companion.configKey

    companion object {
        internal val outputDirKey: CompilerConfigurationKey<String> =
            CompilerConfigurationKey.create("output.dir")

        internal val outputKindKey: CompilerConfigurationKey<String> =
            CompilerConfigurationKey.create("output.kind")

        internal val configKey: CompilerConfigurationKey<String> =
            CompilerConfigurationKey.create("config")

        internal val stdlibModeKey: CompilerConfigurationKey<String> =
            CompilerConfigurationKey.create("stdlib.mode")
    }

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "output.dir",
            valueDescription = "<path>",
            description = "Directory for plugin artifacts (IR dump, PE files). Default: build/",
            required = false,
            allowMultipleOccurrences = false,
        ),
        CliOption(
            optionName = "output.kind",
            valueDescription = "<exe|dll>",
            description = "Assembly kind. Default: exe.",
            required = false,
            allowMultipleOccurrences = false,
        ),
        CliOption(
            optionName = "config",
            valueDescription = "<debug|release>",
            description = "Build configuration: debug adds assembly-level DebuggableAttribute. Default: release.",
            required = false,
            allowMultipleOccurrences = false,
        ),
        CliOption(
            optionName = "stdlib.mode",
            valueDescription = "<lenient|strict>",
            description = "Stdlib coverage strictness: strict fails the compile listing uncovered kotlin.* symbols; lenient warns. Default: lenient.",
            required = false,
            allowMultipleOccurrences = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            "output.dir" -> configuration.put(outputDirKey, value)
            "output.kind" -> {
                check(value in setOf("exe", "dll")) { "Unknown output.kind: $value (expected exe|dll)" }
                configuration.put(outputKindKey, value)
            }
            "config" -> {
                check(value in setOf("debug", "release")) { "Unknown config: $value (expected debug|release)" }
                configuration.put(configKey, value)
            }
            "stdlib.mode" -> {
                // Value validation lives in StdlibMode.fromCli; validating at
                // parse time makes a bad value fail before any compilation.
                StdlibMode.fromCli(value)
                configuration.put(stdlibModeKey, value.lowercase())
            }
            else -> error("Unknown option: ${option.optionName}")
        }
    }
}
