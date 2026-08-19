# ADR 0007: Output-директория плагина через CLI-опцию `output.dir`

- **Дата:** 2026-08-17
- **Статус:** Accepted
- **Связанные:** ADR 0001 (pipeline), ADR 0006 (`just`), задача A-02

## Контекст

До этого момента `DotnetIrGenerationExtension` хардкодил `build/`
в путях артефактов:

- `File("build/ir-dump.txt")` — фиксированное имя дампа IR,
  перетираемое между модулями/файлами.
- `File(buildDir, "$asmName.il")` — директория `build/` зашита в коде
  плагина.

Это нарушает принцип «плагин — только кодогенерация, build-систему
не его забота» (AGENTS.md §«Принципы», ADR 0006). Разные сборочные
сценарии (CI, sandbox, альтернативная build-директория) не могли
перенаправить вывод без правки исходника плагина.

Дополнительно: `runCatching{}.onFailure{}` вокруг IL-эмиссии
отсутствовал, но вокруг dump — присутствовал и просто печатал в
stderr («silent failure»).

## Решение

1. **Плагин не хардкодит `build/`.** Директория вывода передаётся
   через `CompilerConfiguration` (CLI-опция
   `-P plugin:kotlin.dotnet:output.dir=<path>`). Дефолт — `build/`
   для обратной совместимости, но это единственный «build/» в коде
   плагина (fallback в `DotnetCompilerPluginRegistrar`).

2. **Опция регистрируется стандартным путём Kotlin compiler plugin
   API:**
   - `DotnetCommandLineProcessor : CommandLineProcessor` — объявляет
     `CliOption("output.dir", ...)`, кладёт значение в
     `CompilerConfigurationKey<String>`.
   - Обнаруживается через ServiceLoader:
     `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor`.
   - `DotnetCompilerPluginRegistrar.registerExtensions` читает ключ и
     передаёт `File` в конструктор `DotnetIrGenerationExtension`.

3. **Имя IR-дампа** — `ir-dump-<moduleFragment.name>.txt` (один dump
   на модуль, не перетирается между файлами одного модуля).

4. **onFailure — с разделением контракта:**
   - **IR-dump** — best-effort: остаётся под `runCatching`, ошибка
     логируется через `Log.warn` (не `Log.error` — dump не критичен).
   - **IL-генерация** — не silent: `irFile.accept` без `runCatching`;
     любая ошибка оборачивается в `IllegalStateException` с контекстом
     (имя файла, целевой `.il`), битый/пустой `.il` удаляется, и
     исключение летит наружу → `kotlinc` фейлится с понятным сообщением.

## Почему отдельный ADR (а не правка 0001)

ADR 0001 фиксирует pipeline (IR → IL-текст → ilasm). Вопрос «куда
плагин пишет артефакты» — отдельное архитектурное решение о границе
ответственности плагина и build-системы. Вынесение в 0007 сохраняет
0001 сфокусированным.

## Последствия

- `scripts/kotlinc-net.sh` и `justfile` (`_gen-il`) передают
  `-P plugin:kotlin.dotnet:output.dir=build` явно (можно положиться
  на дефолт, но явное лучше).
- `grep -n 'build/' DotnetIrGenerationExtension.kt` → `build/` встречается
  только в дефолте (`File("build")`) регистратора, не в коде путей.
- Ошибка IL-эмиссии (TODO-узел) фейлит компиляцию, не оставляя пустой
  `.il`.
- Для будущих опций (например, флагов уровня детализации дампа) —
  та же схема: `CliOption` + `CompilerConfigurationKey` + чтение в
  `registerExtensions`.

## TODO

- Не делать «профили»/«targets»: одна опция = одна строка (A-02).
  Расширение только при реальной необходимости.
