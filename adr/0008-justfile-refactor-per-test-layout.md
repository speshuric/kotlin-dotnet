# ADR 0008: Рефакторинг `justfile` — per-test layout, реестр тестов, dispatch-скрипты

- **Дата:** 2026-08-19
- **Статус:** Accepted
- **Связанные:** ADR 0006 (`just`), ADR 0007 (`output.dir`)

## Контекст

ADR 0006 зафиксировал `just` как единый диспетчер сборки. К Phase 9
`justfile` разросся до ~200 строк: рецепты `test-*`, `show-il`, `disasm`,
`show-ir` дублировали логику активации env, хардкодили имена файлов
(`Arithmetic.il`), и не параметризовались по test-id. Проблемы:

1. **Нет `build` целиком.** Существовали `plugin` и `runtime` по-отдельности,
   но не «собрать всё». `build debug`/`build release` отсутствовали.
2. **`show-il`/`disasm`/`show-ir` хардкодили `Arithmetic.il`.** Нельзя было
   посмотреть IL/IR другого теста без правки рецепта.
3. **`test` ≠ `test-all`.** `just test` запускал `00-int-add`, а не все тесты.
4. **`clean` — только `build/`.** Не было `clean sdk`/`clean sources`.
5. **Логика в `justfile`.** Рецепты `test-04-loops` (~20 строк), `_gen-il`,
   `_gen-asm` — это bash-код, которому место в `scripts/`, а не в диспетчере.

## Решение

### 1. Per-test layout артефактов

Все сборочные артефакты — в `build/<testid>/` (раньше плоско в `build/`):

```
build/
├── 00-int-add/Arithmetic.il, Arithmetic.dll, kt-out/
├── 02-expr/Expr.il, Expr.dll, kt-out/
├── 03-hello/hello.il, hello.exe, hello.runtimeconfig.json, KotlinDotnetRuntime.dll, ir-dump-<main>.txt
├── 04-loops/Loops.il, Loops.exe, ..., ir-dump-<main>.txt
└── 04-loops-spec/
    ├── while_2_1/while_2_1.il, while_2_1.exe, ...
    ├── while_2_2/...
    ├── dowhile_3_1/...
    └── dowhile_3_2/...
```

Плагину передаётся `-P plugin:kotlin.dotnet:output.dir=build/<testid>`
(для spec — `build/04-loops-spec/<basename>`). Это стало возможным после
исправления бага `output.dir` (см. ниже «Исправление бага output.dir»).

`show-il`/`disasm`/`show-ir` принимают selector: `last` (новейший по mtime),
`all`, `<testid>`, `<glob>` (напр. `04*`).

### 2. Реестр тестов в `scripts/tests.sh`

Source-only bash-библиотека — единый источник правды о тестах. Содержит
таблицу (id → .kt, kind exe/dll, consumer, описание) и функции:
`test_exists`, `test_kt`, `test_kind`, `test_consumer`, `tests_list`,
`resolve_selector` (`all`/`last`/`<glob>` → список id).

### 3. Dispatch-скрипты в `scripts/`

Тяжёлая логика уехала из `justfile` в исполняемые скрипты:

| Скрипт | Назначение |
|---|---|
| `scripts/tests.sh` | Реестр тестов (source-only библиотека) |
| `scripts/build-test.sh` | Сборка + верификация одного теста (gen-il → ilasm → run/verify), mtime-инкрементальность |
| `scripts/build.sh` | Диспетчер сборки: `plugin` \| `runtime` \| `all` + config (debug/release) |
| `scripts/test.sh` | Диспетчер тестов: selector → список id → `build-test.sh` каждый |
| `scripts/show.sh` | `il` \| `ir` \| `disasm` × `last` \| `all` \| `<testid>` \| `<glob>` |
| `scripts/clean.sh` | `all` \| `build` \| `sdk` \| `sources` |

`justfile` стал тонкой обёрткой: каждый рецепт — 1 строка (вызов скрипта),
`bootstrap` — ~10 строк echo-верификации (без build-логики).

### 4. Рецепты `just`

- `build [config="debug"]` — собрать всё (plugin + runtime + все тесты).
- `build-no-test [config="debug"]` — то же без верификации.
- `test [selector="all"]` — variadic: `just test` == `just test-all`,
  `just test 04-loops` — один тест. `just` variadic с дефолтом подтверждён.
- `test-smoke` == `test-short` == `just test 00-int-add`.
- `show-il [selector="last"]` / `disasm` / `show-ir` — параметризуемые.
- `clean [category="all"]` — `all` (bare `clean` = всё, требует пере-bootstrap!),
  `build` (артефакты), `sdk`, `sources`.
- `plugin`, `runtime [config="release"]` — остались для прямой сборки.

### 5. Семантика `clean`

Bare `just clean` (без аргумента) = `clean all` — удаляет `build/`,
`compiler-plugin/build/`, `runtime/bin`, `runtime/obj`, `.sdk/`, `.sources/`
(кроме `README.md`). **Требует полный `just bootstrap`** для восстановления.
`clean build` — только артефакты (SDK/sources сохраняются).

## Исправление бага `output.dir` (ADR 0007)

При реализации per-test layout выяснилось, что `-P plugin:kotlin.dotnet:output.dir=<dir>`
**не работал**: плагин всегда писал в `build/` (fallback). Причина:
`CompilerConfigurationKey.create` создаёт `com.intellij.openapi.util.Key`,
чей `equals` — identity-сравнение (подтверждено байткодом). Ключ был
instance-`val` в `DotnetCommandLineProcessor`; `DotnetCompilerPluginRegistrar`,
создавая `DotnetCommandLineProcessor()` для доступа к ключу, получал *другой*
`Key` → `configuration.get(...)` возвращал null → fallback.

**Фикс:** `outputDirKey` перенесён в `companion object` (singleton), registrar
обращается к `DotnetCommandLineProcessor.outputDirKey` напрямую (без
throwaway-инстанса). Соответствует референс-паттерну Kotlin-плагинов
(`AllOpenConfigurationKeys` — object).

ADR 0007 описывал `output.dir` как рабочий; этот ADR фиксирует фактическое
состояние и исправление.

## debug / release

- `runtime`: `dotnet build runtime/ -c Release|Debug` — реально влияет.
- `plugin`: Gradle `:compiler-plugin:jar` — debug/release no-op (JAR один).
- `build-test.sh`: `--debug`/`--release` принимается, но кодоген плагина не
  ветвится по конфигурации (PoC). `--release` — дефолт для тестов.

debug/release — основа для будущего (флаги кодогена, оптимизация IL). Сейчас
различие только в конфигурации runtime.

## Последствия

- `just build && just test` — стандартный путь; `just test <id>` — точечный.
- `just --list` — короткий список рецептов без bash-вставок.
- Per-test layout изолирует артефакты, упрощает `show-il`/`clean`.
- `scripts/` содержит «библиотеку» для `just` (6 новых файлов).
- C# consumer `.csproj` `HintPath` обновлены под per-test layout.
- Bare `just clean` — деструктивен (требует пере-bootstrap); предупреждение
  в stderr.

## Drawbacks

1. **6 новых bash-файлов** в `scripts/`. Каждый < ~100 строк, single-responsibility.
   Альтернатива (всё в `justfile`) нарушает принцип «минимизировать объём в justfile».
2. **Bare `clean` = `clean all`** — требует полный пере-bootstrap. Сознательное
   решение; `clean build` — безопасная альтернатива.
3. **debug/release — no-op на кодогене** (кроме runtime). Основа для будущего.

## TODO

- `scripts/tests.sh` — при добавлении теста регистрировать его здесь.
  Будущая автоматизация: сканирование `test-projects/` → auto-registry.
