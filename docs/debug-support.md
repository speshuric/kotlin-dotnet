# Поддержка отладочной информации (debug/release) в kotlin-dotnet

Реализовано в K-01/K-02 (см. `TODO/K-debug-builds/`). Этот документ —
сводное описание того, как устроена генерация отладочной информации,
какие источники истины использовались и как всё это проверяется.

## 1. Обзор

Компилятор принимает CLI-опцию `-P plugin:kotlin.dotnet:config=debug|release`
(по умолчанию `release`) и производит:

| Режим | Сборка | Sidecar PDB | Семантика |
|---|---|---|---|
| `release` | без `DebuggableAttribute` | есть (полная отладочная информация) | JIT оптимизирует — как csc без `/debug` |
| `debug` | `DebuggableAttribute(DebuggingModes = 0x0107)` | есть | JIT без оптимизаций — как csc `/debug+` |

Ключевое отличие от csc: у нас PDB пишется в **обоих** режимах (csc
без `/debug` не пишет вовсе). Это осознанное решение — PDB дешёвый,
а единообразие упрощает проверку.

Скрипты пробрасывают конфигурацию автоматически: `build-test.sh`
проверяет **обе** конфигурации для каждого теста; `kotlinc-net.sh`
принимает `--debug` / `--release`.

## 2. DebuggableAttribute (уровень L1)

В debug-режиме на сборку ставится assembly-level атрибут
`System.Diagnostics.DebuggableAttribute` с маской
`DebuggingModes = 0x0107`
(`Default | IgnoreSymbolStoreSequencePoints | EnableEditAndContinue |
DisableOptimizations`) — побитовое совпадение с выводом csc `/debug+`.
Release-сборка атрибута не имеет (тоже как csc).

Механика: TypeRef (`System.Diagnostics.DebuggableAttribute` +
`DebuggingModes`) → MemberRef `.ctor(DebuggingModes)` → CustomAttribute
на AssemblyDefinition со значением `[01 00][07 01 00 00 00][00 00]`
(prolog, int32 arg, 0 named args). Реализация —
`PeReferenceResolver.addAssemblyDebuggableAttribute`.

## 3. Portable PDB (уровень L2)

### 3.1 Формат файла

Portable PDB — это **сырой корень метаданных BSJB** без какой-либо
PE-обёртки. Версия корня — `PDB v1.0`; потоки: `#~`, `#Strings`,
`#US`, `#GUID`, `#Blob`, `#Pdb`. Поток `#Pdb` содержит
`Id` (MVID сборки, 16 байт), `EntryPtTok` (4 байта) и
`ReferencedTypeSystemTokens` — итого 44 байта (эталон csc net10;
замерено, не выдумано).

Сборка образа — `dotnetutils/.../ecma335/PdbBuilder.kt`;
запись потока #Pdb и его заголовок — в `MetadataRootBuilder` /
`MetadataBuilder.serializeMetadataHeader` (флаг
`MetadataSizes.isStandalonePdb`).

### 3.2 Таблицы

Заполняются только debug-таблицы (обычных строк в standalone PDB нет):

| Таблица | Содержимое у нас |
|---|---|
| `Document` (0x30) | по одному на исходник: имя (blob, см. 3.4), SHA-256 хеш исходника, GUID алгоритма SHA-256, language GUID |

GUID'ы — из спеки portable PDB
(`.sources/dotnet-runtime/docs/design/specs/PortablePdb-Metadata.md`,
раздел «Document table rows»; upstream URL:
`docs/design/specs/PortablePdb-Metadata.md` в репозитории dotnet/runtime):

| Назначение | GUID |
|---|---|
| HashAlgorithm: SHA-256 | `8829d00f-11b8-4213-878b-770e8597ac16` |
| Language: Kotlin | `6fa7c4e1-9c0b-4c2e-a1d3-5b7f900ab177` — **кастомный**, Kotlin не зарегистрирован в спеке; spec явно разрешает произвольные значения («the reader can interpret them arbitrarily») |
| `MethodDebugInformation` (0x31) | по строке на метод с точками: Document + blob sequence points (см. 3.3) |
| `LocalScope` (0x32) | по одному на метод с именованными локалами; покрывает всё тело |
| `LocalVariable` (0x33) | attributes=0, index=слот, имя из IR |

Отсутствующие (ImportScope, LocalConstant, StateMachineMethod,
CustomDebugInformation) — сознательно не портированы; при появлении
надобности портить из апстрима.

Sorted-маска tables header для standalone PDB декларирует только
реально присутствующие сортированные таблицы (сейчас LocalScope);
legacy-маска обычной сборки сюда не годится.

### 3.3 Sequence points — дельта-кодировка

Формат зеркалит ридер SRM
(`SequencePointCollection.Enumerator`, апстримный файл
`src/libraries/System.Reflection.Metadata/src/System/Reflection/Metadata/PortablePdb/SequencePointCollection.cs`):

```
compressed(localSignatureRid = 0)
для каждой записи:
  compressed(offset)              // первая — абсолютный, далее дельта
  compressed(deltaLines)          // uint
  deltaLines == 0 ? compressed(deltaColumns)   // uint
                  : compressedSigned(deltaColumns)
  первая запись: compressed(startLine), compressed(startColumn)  // абсолютные
  остальные:     compressedSigned(dStartLine), compressedSigned(dStartColumn)
```

Правила, выстраданные отладкой:
- несколько IR-узлов могут стартовать на одном IL-смещении — нулевая
  дельта оффсета читается ридером как «смена документа» ⇒ дубликаты
  оффсетов отбрасываются (остаётся первый);
- точки с пустым диапазоном (`start == end`) — «hidden»; ридер не
  потребляет их поля начала, что сбивает выравнивание ⇒ такие точки
  не кодируются вовсе;
- метод без осмысленных точек не получает строки MDI вообще: пустой
  blob-хендл невалиден.

Координаты берутся у visitor'а из `IrFileEntry.getSourceRangeInfo`
(0-based) и переводятся в 1-based; захват привязан к позиции буфера
опкодов (`PeBodyEncoder`), т.е. IL-оффсеты всегда согласованы с
реальным кодом.

### 3.4 Имя документа

Формат Roslyn/csc: blob начинается с одного байта-разделителя (`/`),
затем для каждого сегмента пути — compressed-хендл на под-blob кучи
#Blob с UTF8-текстом сегмента. Абсолютный путь даёт первый пустой
сегмент. Реализация — `PePdbEncoder.encodeDocumentName`.

Плоский путь (один chunk с полным текстом) спекой допускается, но
csc так не пишет, а SRM при наших ранних попытках падал — держимся
посегментной схемы как эталонной.

## 4. Конвейер (кто что делает)

```
DotnetIrVisitor            — markSequencePoint(node.sourceRange) на каждом стейтменте
PeIlEmitter                — SeqPointOp псевдо-оп в буфере метода
PeBodyEncoder.encode       — превращает метки в реальные IL-оффсеты
PeIlEmitter.writePortablePdbTo
                           — собирает standalone-метаданные (Document,
                             MDI+SP, LocalScope/LocalVariable) и вызывает
                             PdbBuilder.build()
PdbBuilder (dotnetutils)   — сырой BSJB-образ ("PDB v1.0" + #Pdb stream)
DotnetIrGenerationExtension— записывает <name>.pdb рядом со сборкой
```

`declareLocal(cilType, name)` протаскивает имена локалов из IR
(нужны для LocalVariable); `markSequencePoint` — no-op по умолчанию
в контракте.

## 5. Проверка (e2e-матрица)

Для каждого теста `scripts/build-test.sh` прогоняет **обе**
конфигурации и на каждую проверяет:

1. **Генерация**: сборка + sidecar PDB существуют.
2. **Читаемость SRM**:
   - сборка — `kotlin-dotnet-utils/verifier` (PEReader/MetadataReader);
   - PDB — `kotlin-dotnet-utils/pdbcheck`
     (`MetadataReaderProvider.FromPortablePdbStream`; проверяет
     Documents, MethodDebugInformation, координаты точек, scopes).
3. **Работа в dotnet**: запуск EXE через `dotnet` либо C#-consumer для
   DLL; вывод сравнивается с `expect` — отдельно для каждой конфигурации.

Точечные прогоны одной конфигурации: `build-test.sh <id> --debug`
или `--release`.

Харнесс для ручного исследования PDB (дамп документов, sequence
points, scopes) — `TODO/K-debug-builds/k-02-srm-pdb-oob/artifacts/harness`;
минимальные пары dll+pdb (csc vs наш) — там же в `minimal-cs/` и
`minimal-kt/`; парсер — `artifacts/dump_pdb.py`.

## 6. Источники истины (при расхождении смотреть сюда)

1. **Спека portable PDB**:
   https://github.com/dotnet/runtime/blob/main/docs/design/specs/PortablePdb-Metadata.md
   (поток #Pdb, формат Document.Name, дельта-кодировка sequence points).
2. **ECMA-335 Partition II** (локально `docs/ECMA-335_...pdf`):
   II.24 (метаданные), II.25.3.3 (debug metadata root).
3. **Апстрим SRM** (ридер — эталон семантики):
   `.sources/dotnet-runtime/src/libraries/System.Reflection.Metadata/src/`
   ключевые файлы:
   - `System/Reflection/Metadata/Internal/BlobHeap.cs` —
     `GetDocumentName` (формат имени);
   - `System/Reflection/Metadata/PortablePdb/SequencePointCollection.cs` —
     дельта-ридер точек;
   - `System/Reflection/Metadata/Internal/*.cs` — табличные ридеры
     (LocalScopeTableReader и др., требования sorted).
4. **Эталонные бинарники**: собрать csc-пару командами из
   `TODO/K-debug-builds/k-02-srm-pdb-oob/artifacts/minimal-cs/`
   (csc лежит в `.sdk/dotnet/sdk/*/Roslyn/bincore/csc.dll`);
   готовые — в том же каталоге и `artifacts/reference/ref.pdb`.
5. **Roslyn** (writer-side, если понадобится сверить энкодинг):
   github.com/dotnet/roslyn — `PdbWriter`/`DocumentNameUtilities`;
   в `.sources` не клонирован.

## 7. Известные ограничения

- ImportScope / LocalConstant / StateMachineMethod /
  CustomDebugInformation не заполняются (портить по потребности).
- LocalScope покрывает всё тело метода; точных границ по блокам нет.
- Sequence points ставятся на стейтменты; выражения внутри не
  размечены (Roslyn размечает плотнее).
- Debug Directory в PE не пишется (отладчик ищет PDB по соглашению
  об одинаковом имени рядом со сборкой); порт DebugDirectoryBuilder —
  в REMAINING-ROADMAP (§D).
- dotnet-ildasm не рендерит assembly-level `.custom` и ничего не знает
  о portable PDB — для проверки использовать SRM-харнесс/pdbcheck.
