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
 * Опции (см. ADR 0007, ADR 0010):
 * - `output.dir` — директория, куда плагин пишет артефакты
 *   (IR-dump, `.il`-файлы). Дефолт — `build/` (задаётся в
 *   [DotnetIrGenerationExtension], а не здесь).
 * - `backend` — бэкенд генерации: `il` (дефолт, IL-текст + ilasm) или
 *   `pe` (прямая запись PE через dotnetutils).
 * - `output.kind` — тип сборки для backend=pe: `exe` | `dll`
 *   (для il-пути не используется — режим выбирает ilasm).
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

    internal val backendKey: CompilerConfigurationKey<String> =
        Companion.backendKey

    internal val outputKindKey: CompilerConfigurationKey<String> =
        Companion.outputKindKey

    companion object {
        internal val outputDirKey: CompilerConfigurationKey<String> =
            CompilerConfigurationKey.create("output.dir")

        internal val backendKey: CompilerConfigurationKey<String> =
            CompilerConfigurationKey.create("backend")

        internal val outputKindKey: CompilerConfigurationKey<String> =
            CompilerConfigurationKey.create("output.kind")
    }

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "output.dir",
            valueDescription = "<path>",
            description = "Directory for plugin artifacts (IR dump, .il files). Default: build/",
            required = false,
            allowMultipleOccurrences = false,
        ),
        CliOption(
            optionName = "backend",
            valueDescription = "<il|pe>",
            description = "Code generation backend: pe (direct PE via dotnetutils, default since S6/ADR-0010) or il (IL text + ilasm fallback).",
            required = false,
            allowMultipleOccurrences = false,
        ),
        CliOption(
            optionName = "output.kind",
            valueDescription = "<exe|dll>",
            description = "Assembly kind for backend=pe. Default: exe.",
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
            "backend" -> {
                check(value in setOf("il", "pe")) { "Unknown backend: $value (expected il|pe)" }
                configuration.put(backendKey, value)
            }
            "output.kind" -> {
                check(value in setOf("exe", "dll")) { "Unknown output.kind: $value (expected exe|dll)" }
                configuration.put(outputKindKey, value)
            }
            else -> error("Unknown option: ${option.optionName}")
        }
    }
}
