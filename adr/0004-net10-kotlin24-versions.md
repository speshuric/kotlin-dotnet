# ADR 0004: Версии — Kotlin 2.4.20-RC, .NET 10

- **Дата:** 2026-08-17
- **Статус:** Proposed

## Контекст

AGENTS.md требует "bleeding edge" версии Kotlin и .NET. Конечная цель —
плагин должен работать с локально собранным kotlin compiler и локально
собранным dotnet, но на первом этапе сидим на бинарных сборках.

## Решение

| Компонент | Версия | Источник |
|---|---|---|
| Kotlin | 2.4.20-RC | github.com/JetBrains/kotlin/releases/tag/v2.4.20-RC |
| kotlinc CLI | 2.4.20-RC | `kotlin-compiler-2.4.20-RC.zip` |
| kotlin-compiler-embeddable (Gradle) | 2.4.20-RC | maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/ |
| .NET SDK | 10.0.400 | dot.net/v1/dotnet-install.sh --channel 10.0 |
| JDK | Temurin 21 | api.adoptium.net |
| Gradle | 9.7.0 | services.gradle.org/distributions/gradle-9.7.0-bin.zip |
| .NET runtime sources | release/10.0 | github.com/dotnet/runtime |
| Kotlin sources | v2.4.20-RC | github.com/JetBrains/kotlin |

## Выбор версии Gradle

AGENTS.md требует bleeding edge. KGP 2.4.20-RC поддерживает Gradle **от 7.6.3
без верхней границы** (`GradleCompatibilityCheck.kt` — только проверка снизу).
Сам Kotlin 2.4.20-RC собирается на Gradle 9.5.1, но это не верхняя граница для
пользовательских проектов.

Выбрана **9.7.0** — последний стабильный GA на момент написания (06-Aug-2026).
Предыдущий выбор 8.14.3 давал warning "deprecated, minimum will become 8.14.4
in Kotlin 2.5.0" — с 9.7.0 warning исчез.

## Установка

Всё локально в `.sdk/` (gitignored):

- `.sdk/jdk/` — Temurin 21
- `.sdk/kotlinc/` — kotlin-compiler-2.4.20-RC.zip
- `.sdk/dotnet/` — .NET 10 SDK

Скрипты: `scripts/install-sdks.sh`, `scripts/install-sources.sh`,
`scripts/bootstrap.sh`, `scripts/activate.sh`.

## Регистрация плагина в Kotlin 2.4

В Kotlin 2.4 `ComponentRegistrar` deprecated с `DeprecationLevel.ERROR`.
Используется **`CompilerPluginRegistrar`** (абстрактный класс):

```kotlin
class DotnetCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "kotlin.dotnet"
    override val supportsK2: Boolean = true
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(DotnetIrGenerationExtension())
    }
}
```

+ ServiceLoader-файл `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar`.

## TODO

- При переходе на локально собранный kotlin compiler — переключить
  `compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.20-RC")`
  на `projects.dependencies(...)` или локальный maven repo с собранным
  артефактом.
