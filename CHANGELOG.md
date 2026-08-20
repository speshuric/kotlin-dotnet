# Changelog

Все заметные изменения фиксируются здесь. Формат основан на [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Changed — рефакторинг `justfile` (ADR 0008)

- **Per-test layout артефактов:** все сборочные артефакты теперь в
  `build/<testid>/` (раньше плоско в `build/`). Плагину передаётся
  `output.dir=build/<testid>` через CLI-опцию (ADR 0007). Для spec-тестов
  `04-loops-spec` — поддиректория на каждый `.kt` (`build/04-loops-spec/<basename>/`).
- **`scripts/tests.sh`** — source-only bash-библиотека: реестр тестов
  (id → .kt, kind, consumer, описание), функции `test_exists`/`test_kt`/
  `test_kind`/`tests_list`/`resolve_selector` (`all`/`last`/`<glob>` → id).
  Единый источник правды о тестах.
- **Dispatch-скрипты** в `scripts/` (тяжёлая логика уехала из `justfile`):
  - `scripts/build-test.sh` — сборка + верификация одного теста
    (gen-il → ilasm → run/verify), mtime-инкрементальность, флаги
    `--debug`/`--release`/`--no-test`.
  - `scripts/build.sh` — диспетчер: `plugin` | `runtime` | `all` + config.
  - `scripts/test.sh` — диспетчер: selector → список id → `build-test.sh`.
  - `scripts/show.sh` — `il` | `ir` | `disasm` × `last` | `all` | `<testid>` | `<glob>`.
  - `scripts/clean.sh` — `all` | `build` | `sdk` | `sources`.
- **`justfile` переписан** как тонкая обёртка: каждый рецепт — 1 строка
  (вызов `scripts/*.sh`), ~90 строк вместо ~200. Новые рецепты:
  - `build [config="debug"]` / `build-no-test [config="debug"]` — сборка
    целиком (plugin + runtime + все тесты).
  - `test [selector="all"]` — variadic: `just test` == `test-all`,
    `just test 04-loops` — один тест.
  - `test-smoke` == `test-short` == `just test 00-int-add`.
  - `show-il`/`disasm`/`show-ir` — параметризуемые selector'ом
    (`last`/`all`/`<testid>`/`<glob>`), вместо захардкоженного `Arithmetic.il`.
  - `clean [category="all"]` — bare `clean` = `clean all` (требует
    пере-bootstrap!), `clean build` — только артефакты, `clean sdk`/`sources`.
  - `runtime [config="release"]` — конфигурация.
- **`scripts/kotlinc-net.sh`** — per-test layout (`build/<name>/` вместо
  плоского `build/`), DSH-prelude (GRADLE_USER_HOME/XDG/HOME).
- **C# consumer `.csproj`** — `HintPath` обновлены: `build\00-int-add\Arithmetic.dll`,
  `build\02-expr\Expr.dll` (per-test layout).
- **`scripts/README.md`** — таблица обновлена (6 новых скриптов).

### Fixed — баг `output.dir` (ADR 0007/0008)

- `DotnetCommandLineProcessor.outputDirKey` перенесён в `companion object`.
  `CompilerConfigurationKey.create` создаёт `com.intellij.openapi.util.Key`
  с identity-`equals`; как instance-`val`, ключ в `DotnetCompilerPluginRegistrar`
  получался *другим* `Key` → `configuration.get(...)` возвращал null →
  fallback на `File("build")`. После фикса `output.dir=build/<testid>`
  корректно перенаправляет артефакты. Референс: allopen
  `AllOpenConfigurationKeys` (singleton `object`).

### Added — Phase 9: Циклы (while/do-while) + вызовы функций + интерполяция

- `DotnetIrVisitor`: циклы `while`/`do-while` (IL: `br`/`brtrue`/`brfalse`
  + метки), `break`/`continue` (через стек `LoopContext`), вызовы
  пользовательских top-level функций (`call <ns>.<FileKt>::<method>(...)`),
  `IrStringConcatenation` → `System.String.Concat(object[])` (для 1–2
  аргументов — перегрузки без массива), `IrTypeOperatorCall`
  (`IMPLICIT_COERCION_TO_UNIT` — вычислить и `pop`), `IrComposite`,
  operator `inc`/`dec` (→ `±1` для примитивов, для `x++`/`x--`).
- `IlOpcode`/`IlEmitter`/`TextIlEmitter`: `BOX`, `POP`, `DUP`, `NEWARR`,
  `STELEM_REF`, `LDELEM_REF` (для `string.Concat(object[])`).
- `NameMapper`: экранирование имён методов, совпадающих с CIL-опкодами
  (`box` → `'box'`), чтобы `fun box()` из spec-тестов не ломал ilasm.
- `KotlinOperators`: `INC`/`DEC`.
- `test-projects/04-loops/Loops.kt` — 6 кейсов (while/do-while/break/
  continue/call/concat) → EXE, верифицируется вывод.
- `test-projects/04-loops/spec/` — 4 spec-теста while/do-while (адаптировано
  из `kotlin/tests-spec`), все возвращают "OK".
- `just test-04-loops`, `just test-04-loops-spec` — добавлены в `justfile`;
  `just test-all` — теперь включает 04-loops.
- `for`-loop — оставлен как `TODO("[B-01] for-loop — Phase 11/G-02
  (requires iterators)")` (требует `IntIterator` — Phase 11 / G-02).
- `AGENTS.md` §15 — **DSH-изоляция (read-only корневая ФС)**: обязательный
  prelude (`GRADLE_USER_HOME`/`XDG_RUNTIME_DIR`/`HOME`/`DOTNET_CLI_HOME`
  в writable `build/tmp/`), иначе `just`/`gradlew`/`kotlinc`/`dotnet` падают
  на read-only ФС. Документация особенностей среды исполнения, не проекта.

### Changed — PRE-9: рефакторинг и выравнивание (коммиты 7d37374…8b7d51d)

- **A-07: разбить `org.kotlindotnet.compiler` на подпакеты** (`il/`, `ir/`,
  `util/`). `IlEmitter`/`IlContext`/`IlOpcode`/`TextIlEmitter` → `il/`,
  `IrOrigins`/`KotlinOperators` → `ir/`, `Log` → `util/`.
  Тесты переехали вместе с исходниками.
- **PRE-9 выравнивание — 7 задач через 5 волн** (`0b3e7f4`):
  - `IlEmitter` → интерфейс + контракт «IL корректен» (стек контекстов
    `IlContext`: `TopLevel` → `ContainerClass` → `Method`). `beginX`/`endX`
    парны, `opcode`/`label`/`declareLocal` только внутри метода.
  - `IlOpcode` — enum всех опкодов с `.il`-текстом; единственный источник
    правды (A-04). Короткие формы инкапсулированы в `TextIlEmitter`.
  - `IrOrigins` — константы `debugName` IR-origin (`PLUS`/`GT`/`EQEQ`/...).
  - `KotlinOperators` — имена operator-функций (`plus`/`minus`/`not`/...).
  - `Log` — обёртка над stderr с уровнями `info`/`warn`.
  - `DotnetIrVisitor` — B-01 scaffolding: `override fun visitXxx` для всех
    IR-узлов карты; реализованные делегируют в `emitXxx`, нереализованные
    бросают `TODO("[B-01] <узел> — Phase N")` (fail-fast, не silent empty .il).
  - `DotnetCommandLineProcessor` + ADR 0007 — `output.dir` через CLI-опцию
    (`-P plugin:kotlin.dotnet:output.dir=...`), плагин не хардкодит `build/`.
  - `DotnetIrGenerationExtension` — ошибка эмиссии фейлит компиляцию
    (IllegalStateException с контекстом), не best-effort.
- **TODO-план выравнивающих задач** (`fa2df10`): `TODO/README.md` + 17 файлов
  задач (A-инфраструктура, B-visitor-scaffolding, C-typemapper,
  D-stdlib, E-ir-platform, F-annotations, G-internal-design, H-test-migration).
  Разметка «до/после Phase 9», зависимости, практики исполнителей.
- **Документация** (`7d37374`):
  - `docs/il-reference.md` — справочник CIL-синтаксиса и опкодов.
  - `docs/references.md` — ссылки на kotlin/dotnet-runtime/ilasm/dnlib.
  - `docs/generics-strategy.md` — reified generics vs erasure, variance.
  - `AGENTS.md` — cleanup: вынесены docs/, обновлён roadmap, отмечено done.
- **`TODO/PHASE-9.md`** (`5e8739f`) — полный план Phase 9: задачи 9.1–9.8,
  IR-наблюдения (дампы while/do-while/break/continue/for/concat),
  IL-паттерны, критерии приёмки. `for` помечен опциональным (Phase 11/G-02).

### Added — `kotlinc-net.sh` CLI компилятор

- `scripts/kotlinc-net.sh` — единая точка компиляции `.kt` → `.exe`/`.dll`:
  ```
  ./scripts/kotlinc-net.sh <file.kt>              # → build/<name>.exe
  ./scripts/kotlinc-net.sh <file.kt> -dll         # → build/<name>.dll
  ./scripts/kotlinc-net.sh <file.kt> -o out.exe   # явное имя
  ./scripts/kotlinc-net.sh <file.kt> --rebuild-plugin
  ```
  Копирует `KotlinDotnetRuntime.dll` и генерирует `runtimeconfig.json` для EXE.
- `just compile <file.kt>` — обёртка над `kotlinc-net.sh`.
- Демо-секция в README.

### Added — Phase 8: Runtime + hello world (EXE)

- `runtime/` — KotlinDotnetRuntime (C# class library, net10.0):
  - `Print.cs` — `Kotlin.Runtime.Print.println/print` для всех примитивов.
- `StdlibResolver.kt` — маппинг `kotlin.io.println`/`print` → runtime-вызовы.
- `main()` → EXE с `.entrypoint` (раньше только DLL).
- `test-projects/03-hello/` — `fun main() { println("Hello, .NET!") }` →
  `hello.exe` печатает "Hello, .NET!".
- `just runtime` — собирать `KotlinDotnetRuntime.dll`.
- `just test-03-hello` — полный pipeline hello world (EXE + runtimeconfig.json).
- `just test-all` — все тесты (00-int-add, 02-expr, 03-hello).

### Added — Phase 7: Расширение выражений

- `test-projects/02-expr/` — арифметика, локальные переменные, if/when,
  сравнения, bool (&&/||/!), Long, Double, String-eq.
- `IlEmitter`: locals (`.locals init`), метки (`IL_N:`), `declareLocal`.
- `DotnetIrVisitor`: `IrVariable`, `IrSetValue`, `IrWhen`/`IrBranch`
  (if/when/ANDAND/OROR), `IrBlock`, все `IrConstKind` (Boolean/Long/Double/
  String/Char/Float), сравнения (GT/LT/GE/LE/EQEQ), унарные (neg/not),
  логические (and/or/xor), сдвиги (shl/shr/ushr).
- `just test-02-expr`, `just test-all`.
- `gradle.properties` — `org.gradle.java.installations.paths=.sdk/jdk`.

### Changed

- **`just` как единая точка входа для сборки** (ADR 0006).
  `justfile` в корне: `just bootstrap`, `just test`, `just list`,
  `just clean`, etc. mtime-инкрементальность на `il`/`dll` шагах.
  Удалены дубликаты `scripts/bootstrap.sh` и `scripts/build-test-add.sh`.
- Gradle 8.14.3 → **9.7.0** (bleeding edge, последний стабильный GA).
  Warning про deprecated 8.x исчез. KGP 2.4.20-RC поддерживает Gradle 9.x
  без верхней границы (`GradleCompatibilityCheck.kt`).

### Added — Phase 0–6: минимальный pipeline `test_add`

- Скелет проекта: `AGENTS.md`, `CLAUDE.md -> AGENTS.md`, `.gitignore`.
- `scripts/` для активации локального окружения (`activate.sh`, `deactivate.sh`,
  `install-sdks.sh`, `install-sources.sh`, `bootstrap.sh`, `build-test-add.sh`).
- Локальные SDK (всё в `.sdk/`, gitignored):
  - JDK Temurin 21
  - kotlinc 2.4.20-RC
  - .NET 10.0.400 SDK + global tools (`ilasm-cli`, `dotnet-ildasm`)
  - Gradle 8.14.3
- Локальные исходники (в `.sources/`, gitignored):
  - JetBrains/kotlin @ v2.4.20-RC (shallow clone)
  - dotnet/runtime @ release/10.0 (shallow clone)
- Compiler plugin (Kotlin 2.4.20-RC, K2):
  - `DotnetCompilerPluginRegistrar` — регистрация через `CompilerPluginRegistrar`
    (не устаревший `ComponentRegistrar`).
  - `DotnetIrGenerationExtension` — перехват IR через `IrGenerationExtension`.
  - `DotnetIrVisitor` — обход IR-дерева (`IrFile → IrSimpleFunction →
    IrBlockBody → IrReturn → IrCall(PLUS) → IrGetValue`).
  - `IlEmitter` — билдер IL-текста (assembly header, container class, static method).
  - `TypeMapper` — маппинг примитивных типов Kotlin → CIL.
  - `NameMapper` — маппинг package → namespace, file → `<File>Kt`, method verbatim.
- Тестовый проект `test-projects/00-int-add/`:
  - `Arithmetic.kt` — `package org.example.math; public fun test_add(a: Int, b: Int) = a + b`
  - `manual.il` — hand-written CIL (для отладки `ilasm` без компилятора).
  - `csharp-test/` — C# consumer (`<Reference HintPath>` на сгенерированную DLL).
- End-to-end pipeline работает:
  `Arithmetic.kt` → компилятор-плагин → `Arithmetic.il` → `ilasm` →
  `Arithmetic.dll` → C# consumer печатает `5`.

### Documentation

- ADRs:
  - `0001-pipeline-ir-to-il.md` — выбор IR → IL-текст → ilasm.
  - `0002-runtime-library.md` — роль KotlinDotnetRuntime (placeholder).
  - `0003-ir-state-at-interception.md` — состояние IR на перехвате (сценарий A).
  - `0004-net10-kotlin24-versions.md` — фиксация версий.
  - `0005-name-mapping.md` — мэппинг имён Kotlin ↔ .NET.
