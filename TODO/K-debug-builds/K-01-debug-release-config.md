# K-01: Сборка в debug и release режимах

- **Статус:** TODO (исследование зафиксировано 2026-08-26)
- **Разметка:** POST-PoC (L1 можно раньше — см. «Уровни»)
- **Зависимости:** Phase 10.9 (PE — единственный вывод), dotnetutils (I-01)
- **ADR:** не требуется (решение техническое, конфликтов архитектуры нет)

## Постановка вопроса

«Могу ли я собирать сборку в debug и в release режиме? Что для этого нужно?»

## Текущий статус (проверено по коду)

Флаг `config=debug|release` существует (`just build debug|release`,
`scripts/build.sh`), но влияет **только на C# runtime DLL**
(`dotnet build runtime/ -c Debug|Release`). Сгенерированные компилятором
Kotlin-сборки **от конфига не зависят вообще**: биты одинаковые в обоих
режимах.

Что есть/чего нет сегодня:

| Аспект | Статус |
|---|---|
| `DebuggableAttribute` на assembly | нет (grep по `PeIlEmitter.kt` — 0) |
| Portable PDB (`.pdb`, sequence points) | нет; в `dotnetutils` debug-таблицы существуют только как записи `TableIndex` (0x30–0x37) |
| Имена локалов в отладочных таблицах | нет (локалы кодируются через standalone sig без имён) |
| Sequence points из IR | нет (visitor не читает line map файла) |
| Семантика сгенерированной сборки | фактически **release**: без `DebuggableAttribute` JIT оптимизирует, стектрейсы без номеров строк |

Вывод: **сборка в двух режимах сейчас невозможна** — переключатель
есть только у runtime-DLL. Любая сборка плагина ведёт себя как release.

## Что нужно

### Уровень 1 — флаг debug/release (дёшево, без PDB)

1. `DebuggableAttribute` (System.Diagnostics.DebuggableAttribute,
   ctor(DebuggingModes)) на assembly:
   - release: атрибут не ставим (или `Default`) — как делает csc;
   - debug: `DisableOptimizations | EnableEditAndContinue |
     IgnoreSymbolStoreSequencePoints | Default` (= 0x107).
   - Механика: TypeRef → MemberRef → константный blob →
     `addCustomAttribute(assemblyHandle, ...)` — таблица CustomAttribute
     в dotnetutils уже поддержана.
2. CLI-опция плагина `-P plugin:kotlin.dotnet:config=debug|release`
   (+ ключ в `DotnetCommandLineProcessor`, проброс из registrar в
   extension/emitter). Реестр опций — `docs/plugin-options.md`.
3. Проброс из скриптов: `kotlinc-net.sh`, `build-test.sh`, `build.sh`
   передают config в плагин (сейчас config до kotlinc не доходит).

Эффект L1: JIT не оптимизирует debug-сборку, корректные стектрейсы
без инлайна; в отладчике можно ставить breakpoints на строки метода
вручную (без пошагового маппинга строк). PDB по-прежнему нет.

### Уровень 2 — настоящий debug-опыт (portable PDB)

4. Порт PdbBuilder из upstream SRM (System.Reflection.Metadata.Ecma335):
   standalone-метаданные #Pdb + #Us, таблицы Document /
   MethodDebugInformation / LocalScope / LocalVariable.
5. Sequence points: visitor при эмиссии операторов фиксирует
   `(offset, startLine, startCol, endLine, endCol)` через
   `IrFile.fileEntry.lineMap`.
6. Имена локалов: LocalVariable + LocalScope вокруг тел методов.
7. Запись PDB: sidecar `<asm>.pdb` или embedded; в PE — Debug Directory
   (IMAGE_DEBUG_TYPE_CODEVIEW, GUID+checksum PDB) — требует расширения
   `ManagedPEBuilder` (сейчас Debug Directory не пишется).
8. Верификация: открыть сборку+PDB настоящим SRM (`PEReader` +
   `MetadataReader` с `MetadataReaderOptions.Default` / pdb provider),
   сверить количество sequence points; ручной прогон под отладчиком.

#### Как обеспечивается соответствие строк исходника и кода сборки

Sequence point — запись «IL-оффсет → диапазон строк». Цепочка от
исходника до PDB:

1. **Координаты — из фронтенда.** У `IrFile` есть `fileEntry`
   (`IrFileEntry`) с line map исходника; для каждого IR-узла известен
   его диапазон в файле → `(startLine, startCol, endLine, endCol)`
   берётся у узла/через `fileEntry`. Компилятор сам ничего не парсит.
2. **Привязка к IL — в эмиттере.** Оффсеты появляются только при
   отложенном кодировании тел: PeIlEmitter буферизует список опов и
   расставляет оффсеты в `encodeBody` (метки уже резолвятся так).
   API эмиттера: `markSequencePoint(line, col)` перед эмиссией оператора
   привязывает метку к текущей позиции буфера опов; при кодировании
   тела метка переводится в реальный IL-оффсет.
3. **Запись в PDB.** На метод — blob в `MethodDebugInformation`:
   список sequence points + ссылка на строку `Document` (путь к .kt,
   hash-алгоритм и хеш контента). Локалы — `LocalScope`/`LocalVariable`.
4. **Связка сборка↔PDB.** Debug Directory entry PE
   (`IMAGE_DEBUG_TYPE_CODEVIEW`) несёт GUID + checksum PDB — по ним
   рантайм/отладчик находит sidecar `.pdb`.
5. **Потребление.** Breakpoint на строке: отладчик ищет ближайший
   sequence point с этой строкой → IL-оффсет → нативный адрес после JIT.

Без шагов 2–3 соответствие не существует даже при наличии PDB:
координаты должны быть захвачены во время эмиссии (шаг 2), а не задним
числом по дампу.

### Приёмка

- L1: `just build debug` vs `just build release` дают сборки, различающиеся
  наличием `DebuggableAttribute`; verifier зелёный в обоих случаях;
  04-loops-spec остаётся зелёным (P5).
- L2: `dotnet-ildasm --debug` (или SRM-харнесс) показывает sequence points;
  breakpoint по строке исходника срабатывает.

## Рекомендация

Сделать L1 отдельной задачей после J-01 (декомпозиция PeIlEmitter —
атрибут логично добавить в уже выделенный writer-слой). L2 — после
базовых фаз языка (13+), рядом с H-01: без стабильного кодогена
sequence points будут постоянно разъезжаться.
