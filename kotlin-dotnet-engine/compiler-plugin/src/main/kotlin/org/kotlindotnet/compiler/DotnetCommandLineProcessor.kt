package org.kotlindotnet.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

/**
 * Обработка CLI-опций плагина `kotlin.dotnet` (передаются через
 * `kotlinc -Xplugin=... -P plugin:kotlin.dotnet:<option>=<value>`).
 *
 * Опции (см. ADR 0007):
 * - `output.dir` — директория, куда плагин пишет артефакты
 *   (IR-dump, `.il`-файлы). Дефолт — `build/` (задаётся в
 *   [DotnetIrGenerationExtension], а не здесь).
 *
 * Принцип: плагин не хардкодит `build/` — путь получает через
 * [CompilerConfiguration]. Один фокус — одна опция (A-02).
 */
class DotnetCommandLineProcessor : CommandLineProcessor {

    override val pluginId: String = "kotlin.dotnet"

    /**
     * Ключ конфигурации: директория вывода плагина.
     * Хранится как `String` (а не `File`) — плагин сам строит `File`
     * в точке использования. Дефолт (`build/`) — тоже в точке
     * использования, чтобы registrar/processor не знали про него.
     *
     * **Singleton (companion object):** [CompilerConfigurationKey.create]
     * внутри создаёт [com.intellij.openapi.util.Key], чей `equals` —
     * identity-сравнение (подтверждено байткодом `Key.create`/`Key.equals`).
     * Если ключ — instance-`val`, то `DotnetCompilerPluginRegistrar`,
     * создавая `DotnetCommandLineProcessor()` для доступа к ключу,
     * получит *другой* `Key` → `configuration.get(...)` вернёт null
     * → fallback на `File("build")`. Помещение ключа в companion
     * гарантирует один и тот же `Key`-объект в `processOption`
     * и в `registerExtensions`. См. ADR 0007, референс: allopen
     * `AllOpenConfigurationKeys` (object).
     */
    internal val outputDirKey: CompilerConfigurationKey<String> =
        Companion.outputDirKey

    companion object {
        internal val outputDirKey: CompilerConfigurationKey<String> =
            CompilerConfigurationKey.create("output.dir")
    }

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "output.dir",
            valueDescription = "<path>",
            description = "Directory for plugin artifacts (IR dump, .il files). Default: build/",
            required = false,
            allowMultipleOccurrences = false,
        )
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            "output.dir" -> configuration.put(outputDirKey, value)
            else -> error("Unknown option: ${option.optionName}")
        }
    }
}
