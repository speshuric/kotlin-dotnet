# Changelog

Все заметные изменения фиксируются здесь. Формат основан на [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added — debug/release режимы генерируемой сборки (K-01 L1)

- CLI-опция плагина `-P plugin:kotlin.dotnet:config=debug|release`
  (дефолт `release`); `debug` ставит assembly-level
  `DebuggableAttribute(DebuggingModes = 0x0107)` — как csc `/debug+`:
  JIT без оптимизаций, корректные стектрейсы.
- Скрипты: `build-test.sh` пробрасывает свой `--debug/--release` в плагин;
  `kotlinc-net.sh` получил флаги `--debug`/`--release`.
- dotnetutils: `MetadataTokens.assemblyDefinitionHandle` (паритет upstream).

### Removed — IL-текстовый путь (ilasm) удалён; PE — единственный вывод (ADR 0012, Phase 10.9)

- Удалены `TextIlEmitter.kt`, `IlContext.kt` и тест `IlEmitterContractTest`;
  интерфейс `IlEmitter` очищен (`text()` и `beginStaticMethod()` убраны,
  `endStaticMethod` переименован в `endMethod`).
- Из `DotnetIrGenerationExtension` удалена il-ветка и параметр `backend`;
  CLI-опция плагина `backend` снята с регистрации — неизвестная опция
  теперь fail-fast ошибка (чужие конфиги с `-P plugin:kotlin.dotnet:backend=il`
  начнут падать — осознанно).
- `NameMapper.methodName` больше не экранирует CIL-слова одинарными
  кавычками (`RESERVED_CIL_WORDS` удалён); `PeIlEmitter` не снимает кавычки.
- Скрипты: из `build-test.sh` удалён il-путь (gen-il → ilasm) вместе с
  mtime-инкрементальностью по `.il` — pe теперь всегда пересобирается;
  артефакты пишутся напрямую в `build/<id>/` (без подкаталога `pe/`,
  swap-танец для dll-консьюмеров убран). Значение `backends=il` в
  `test.properties` — ошибка конфигурации. Из `kotlinc-net.sh` убран флаг
  `-il`; из `show.sh`/`justfile` — kind `il`/рецепт `show-il`;
  `install-sdks.sh` больше не ставит NuGet-пакет `ilasm-cli`, PATH-шимы
  `ilasm` убраны. `_resolve_last` переведён с `*.il` на `ir-dump-*.txt`.
- Тесты: удалены `test-projects/00-int-add/manual.il|manual.dll`.
### Changed — dotnetutils: дескрипторы хэндлов вместо констант

- Во всех хэндлах пары констант `TOKEN_TYPE`/`TOKEN_TYPE_SMALL` заменены на
  свойства-геттеры companion object (`internal val tokenType` /
  `tokenTypeSmall` / `className`); геттеры инлайнятся JIT, но формируют
  неявный общий интерфейс для будущей унификации хэндлов. Хардкод
  `TokenTypeIds.*`/`HandleType.*` в `toHandle()`/`toEntityHandle()` и в
  сообщениях фабрик устранён; `toString()` строится через `className`.
- Охват: 25 row-handle'ов (+ их `from*`), кучевые `BlobHandle`/`GuidHandle`/
  `UserStringHandle` (`tokenTypeSmall`, табличный токен неприменим),
  `StringHandle`/`NamespaceDefinitionHandle` (composite vType — только
  `className`), `LabelHandle`. Таблица применимости по каждому типу —
  `docs/dotnetutils-struct-parity.md` §5.
- Следующий шаг (обсуждается отдельно): сведение трёх свойств в объект-
  дескриптор.

### Changed — dotnetutils: value-holder классы приведены к data classes

- Строки таблиц метаданных в `MetadataRows.kt` (34 класса) теперь
  `internal data class` — заголовок файла это декларировал с момента
  порта, а код не соответствовал.
- `MethodBodyStreamEncoder.MethodBody` и `ReservedBlob<THandle>` — тоже
  `data class`: в апстриме это `public readonly struct`.
- Аудит паритета «upstream struct → data/value class» завершён; реестр и
  исключения зафиксированы в `docs/dotnetutils-struct-parity.md`
  (см. также ADR 0009). Цель — механическая сверка API при будущей
  компиляции модуля в .NET: data/value classes конвертируются в структуры.
- Дочинены остатки аудита: `Blob` (ручные equals/hashCode заменены
  генерированными — семантика идентична), `ControlFlowBuilder.BranchInfo`
  и `ExceptionHandlerInfo` (апстримные private struct).
- Намеренно НЕ переведены (задокументировано): курсоры
  `BlobReader`/`BlobWriter`, энкодеры-фасады (`*Encoder` — readonly
  struct в апстриме, но состояние = builder+offset),
  `BlobDictionary.Entry` (ByteArray-равенство).

### Changed — реестр тестов: 1 тест = 1 папка, без хардкода имён

- `scripts/tests.sh` больше не содержит имён тестов: id = имя папки в
  `test-projects/`, содержащей `test.properties`; папка без свойств
  тестом не считается. `TEST_IDS`/`tests_list` строятся обходом каталога.
- Атрибуты теста (`kind`/`backends`/`sources`/`consumer`/`expect`/`desc`/
  `type`) читаются из `test.properties`; формат и схема ключей —
  `docs/test-format.md`.
- `04-loops-spec` выделен в собственную папку (spec-файлы переехали из
  `04-loops/spec/`); multi-source тесты собираются per-source.
- Особый путь `05-pe-hello` (образ строит Gradle-тест) переключён на
  свойство `type=gradle-image` вместо сравнения по id; ожидания выводов
  всех тестов перенесены в `expect` (с `\n`-escape).
- `scripts/build-test.sh`: универсальные `_verify`/`_verify_pe` без
  case-таблиц по id; ветки il/pe управляются свойством `backends`.

### Added — Phase 10: классы (TypeDef, поля, конструкторы, instance-методы)

- `IrClass` → TypeDef в PeIlEmitter: поля (`addFieldDefinition`, private
  бэкинг-поля), конструкторы (`0x1886`), instance-методы (`0x0086`),
  диапазоны fieldList/methodList по группам типов (каскад с конца);
  `<Module>` + контейнер + классы. Синтез дефолтного `.ctor` как страховка
  (K2 сам кладёт primary ctor в IR).
- Интерфейсы: `Interface|Abstract` (extends=nil) +
  `addInterfaceImplementation`; пустые интерфейсы.
- Наследование: `IrDelegatingConstructorCall` → `ldarg.0` + args +
  `call base::.ctor`; `IrConstructorCall` → args + `newobj`.
- Instance-вызовы → `callvirt`; dispatch receiver = `arguments[0]`;
  fake overrides спускаются по `overriddenSymbols`. Свойства — через
  дефолтные аксессоры `<get-x>`/`<set-x>` (обычные instance-методы).
- open/override (10.7): Virtual|NewSlot / Virtual|ReuseSlot; полиморфизм
  через базовую переменную.
- Инициализаторы полей инжектируются в primary ctor (в K2 IR они висят на
  `FIELD.EXPRESSION_BODY`, в теле ctor их нет).
- Тест `05-classes` (pe-only, реестр `test_backends`): классы, var/val,
  наследование, интерфейс, полиморфизм; verifier OK.
- Verifier расширен детальным дампом (attrs/extends/диапазоны/TypeRef-scope).

Ловушки зафиксированы (детали — TODO/PHASE-10.md §«Итоги»):
- **TypeAttributes.Public = 0x1** (не 0x6 — это MethodAttributes): 0x6
  перекочевал из таблицы флагов методов плана фазы; флаги класса с такой
  видимостью дают невалидный top-level тип → TypeLoadException
  «format is invalid» только при первой загрузке типа.
- Ссылки на свои типы — прямые TypeDef с предвычисленными хэндлами
  (каноничная форма, как у csc/ilasm). NIL-scope TypeRef тоже работает на
  net10 CLR (проверено экспериментом), но компиляторами не используется.
- У конструкторов нет this-параметра в IR → параметры сдвинуты на arg+1.
- Операторные имена (`inc`/`plus`) обрабатываются как опкоды только для
  callee из `kotlin.*` — иначе коллизия с пользовательскими методами;
  системное решение вынесено в TODO F-02 (мэппинг имён).
- Override обязан быть виртуальным даже в final-классе (иначе скрывает
  слот базы и ломает полиморфизм).

### Changed — dotnetutils: доступ к номерам строк только через MetadataTokens

- `rowId` хэндлов остался **internal** — публичная поверхность API
  сохранена паритетной апстримному SRM (там RowId тоже не экспортируется;
  подтверждено по ref-сборке System.Reflection.Metadata). Промежуточно
  внесённое расширение видимости откачено.
- PeIlEmitter для диапазонов fieldList/methodList использует канонический
  `MetadataTokens.getRowNumber(handle.toEntityHandle())` (API уже было в
  порте, зеркалит апстримный `GetRowNumber(EntityHandle)`).

### Added — порт System.Reflection.Metadata на Kotlin: модуль `dotnetutils` (ADR 0009, итерации I0–I8)

Полный write-path + минимальный read-path SRM на чистом Kotlin stdlib
(`org.kotlindotnet.dotnetutils.system.reflection`), пакет зеркалит
неймспейсы оригинала. База порта зафиксирована: dotnet/runtime
`release/10.0` @ `4a4758eb`.

- **I0**: калибровка — enum'ы (`ILOpCode`, `TableIndex`, `Machine`,
  `CorFlags`, …), value-классы `Handle`/`EntityHandle`/`LabelHandle`,
  константы `HandleType`/`TokenTypeIds`.
- **I1**: 30 типизированных хэндлов таблиц/куч; отсечены
  HandleCollections (~1.9k строк reader-side).
- **I2**: внутренние утилиты — `BitArithmetic`, `BlobUtilities`
  (LE/BE писатели, UTF-8 с суррогатами), `Hash`, `Blob`/`BlobBuilder`/
  `BlobWriter`/`BlobContentId` (kotlin.uuid).
- **I3**: `CodedIndex` (13 семейств), `MetadataTokens`, `ILOpCodeExtensions`.
- **I4**: ядро метаданных — `MetadataBuilder` (+кучи #Strings/#US/#Blob/
  #Guid), `MetadataSizes`, `SerializedMetadataHeaps`, `MetadataRootBuilder`.
- **I5**: IL-энкодинг — `InstructionEncoder`, `ControlFlowBuilder`,
  `ExceptionRegionEncoder`, `SwitchInstructionEncoder`,
  `MethodBodyStreamEncoder`.
- **I6**: PE write-path — `PEHeader(Builder)`, `CoffHeader`, `CorHeader`,
  `SectionHeader`, `PEDirectoriesBuilder`, `ManagedTextSection`
  (IAT/import table/startup stub), `PEBuilder`, `ManagedPEBuilder`,
  `ResourceSectionBuilder`.
- **I7**: энкодеры сигнатур и custom attributes (`BlobEncoders`,
  `SignatureHeader`, enum'ы `Signature*`) — 24 структуры.
- **I8** (ADR 0011): минимальный MetadataReader — парс root/streams,
  кучи, строки 11 эмитируемых таблиц, `PEReader`-lite (заголовки,
  rva→offset, method body tiny/fat + exception regions),
  `BlobReader` (compressed integers — зеркало SRM).

Отсечено осознанно: EnC/WinMD/Portable PDB/DebugDirectory, пулинг,
Stream/MemoryBlock, провайдеры декодеров, TypeName*/AssemblyNameInfo,
HandleComparer. Причины — в шапках файлов и
`TODO/I-dotnetutils/test-comparison.md`.

Тесты: **220 → 242**, включая полные порты CompressedIntegerTests.cs,
BlobEncodersTests.cs, BlobTests.cs и round-trip writer→reader сетку
(ADR 0011). Попутные исправления fidelity:
- compressed signed integer — знак в LSB всего raw-значения,
  знакорасширение масками (не инверсия бита);
- `writeUTF8(allowUnpairedSurrogates=false)` заменял U+FFFD любые
  символы ≥0x800 вместо только непарных суррогатов;
- coded index widths: ResolutionScope/TypeDefOrRef = 2 бита,
  MemberRefParent = 3 бита.

### Added — майлстон M1: EXE целиком Kotlin-кодом

- `HelloWorldImage` (тест-фикстура): сборка hello-world EXE через
  `MetadataBuilder` + `InstructionEncoder` + `MethodBodyStreamEncoder` +
  `ManagedPEBuilder` без ilasm.
- Тест `05-pe-hello` в реестре: запуск через `dotnet`, верификация
  C#-harness'ом (`kotlin-dotnet-utils/verifier` — настоящий SRM),
  dotnet-ildasm. Ловушка зафиксирована: `MethodAttributes.Static =
  0x0010` не входит в access-mask — entry point без Static даёт
  TypeLoadException «The signature is incorrect».

### Changed — PE-бэкенд компилятора по умолчанию (ADR 0010, план COMPILER-INTEGRATION)

- Новый `pe/PeIlEmitter` в compiler-plugin: реализация `IlEmitter` над
  dotnetutils; текстовые member-ref'ы visitor'а парсятся в метаданные,
  собственные вызовы резолвятся в MethodDef (тела кодируются отложенно —
  forward-вызовы без второго прохода IR); локали через BlobEncoders;
  сериализация образа `writeAssemblyTo()`.
- CLI плагина: `backend=il|pe` (дефолт **pe** с S6), `output.kind=exe|dll`;
  `kotlinc-net.sh -il` — legacy-путь, `-pe` сохранён для совместимости;
  ilasm больше не нужен для основного пути (fallback сохранён до удаления
  TextIlEmitter отдельным коммитом).
- Вся e2e-сетка зелёная на обоих бэкендах: build-test.sh после il-прогона
  повторяет тест через pe (те же ожидания + C#-verifier на каждый
  pe-артефакт; dll-тесты временно подменяют артефакт по HintPath).
- TextIlEmitter помечен @Deprecated.

### Added — `kotlin-dotnet-utils/`

.NET-сторона проекта: C#-harness `verifier/` открывает собранные образы
настоящим `System.Reflection.Metadata` (PEReader/MetadataReader), печатает
дамп заголовков/таблиц/IL и `VERIFIER OK`. README с использованием.

### Changed — прочее

- Тестовые классы dotnetutils переименованы: сняты префиксы итераций
  (I0/I2/I3/I4/I6 → говорящие имена).
- `EntityHandle.NIL` — публичная nil-константа.
- Upstream-пин базы порта задокументирован в трёх местах (ADR 0009,
  README модуля, REMAINING-ROADMAP).

### Documentation — ADR

- `0009-srm-port-to-kotlin.md` — порт SRM (Accepted).
- `0010-compiler-plugin-pe-backend.md` — прямая генерация PE (Accepted).
- `0011-minimal-metadata-reader.md` — минимальный reader (Accepted).
- Сверка тестов апстрим ↔ проект: `TODO/I-dotnetutils/test-comparison.md`.

### Changed — реорганизация: JDK-область `kotlin-dotnet-engine/`

- Gradle-корень перенесён из корня репозитория в `kotlin-dotnet-engine/`
  (build.gradle.kts, settings.gradle.kts, gradle.properties, wrapper).
- `compiler-plugin/` переехал в `kotlin-dotnet-engine/compiler-plugin/`.
- Новый модуль `kotlin-dotnet-engine/dotnetutils/` — скелет порта
  write-path System.Reflection.Metadata на Kotlin
  (`org.kotlindotnet.dotnetutils.system.reflection.metadata`); собирается,
  содержимое — в следующих итерациях.
- Скрипты обновлены под новые пути (`scripts/{build,build-test,test,
  kotlinc-net,clean}.sh`, justfile bootstrap). Toolchain JDK:
  `../.sdk/jdk` относительно корня engine.

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
