# Analysis 01: Namespaces and API inventory of the SRM write-path

- **Задача:** [I-01](../I-01-metadata-builder.md)
- **Статус:** на утверждении
- **Источник:** `.sources/dotnet-runtime/src/libraries/System.Reflection.Metadata/src/` (release/10.0)

## 1. Маппинг namespaces → пакеты Kotlin

| C# namespace | Kotlin package | Комментарий |
|---|---|---|
| `System.Reflection.Metadata` | `org.kotlindotnet.dotnetutils.system.reflection.metadata` | ядро: Blob*, хэндлы, IL-энумерации |
| `System.Reflection.Metadata.Ecma335` | `...system.reflection.metadata.ecma335` | MetadataBuilder, таблицы, Encoding |
| `System.Reflection.PortableExecutable` | `...system.reflection.portableexecutable` | PE-запись (заголовки, секции) |
| `System.Reflection.Internal` | `...system.reflection.internal` | только подмножество (см. §6) |

## 2. Ядро: blobs и хэндлы (`system.reflection.metadata`)

| Тип | Строк | Вердикт | Роль |
|---|--:|---|---|
| `BlobBuilder` (+`.Enumerators`) | 1290 | **включить** | центральный чанковый писатель байтов |
| `BlobWriter` | 535 | **включить** | запись в зарезервированные области; fixup'ы PEBuilder, user strings |
| `BlobWriterImpl` | 293 | **включить** | compressed integers, примитивы записи |
| `Blob` | 23 | включить | view над byte[] |
| `ReservedBlob<T>` | 26 | включить | blob с зарезервированным местом |
| `BlobContentId` | 132 | включить | content-hash id (MVID) |
| `Handle` / `EntityHandle` / `HandleKind` | 395 | **включить** | токены метаданных, база всех Add*-методов |
| `IL/ILOpCode` | 227 | **включить** | полная таблица опкодов (ushort) |
| `IL/ILOpCodeExtensions` | 239 | включить | размеры операндов, кодирование |
| `IL/ExceptionRegion` (+Kind) | 97 | включить | нужен ExceptionRegionEncoder |

Итого ≈ **3150 строк**.

## 3. Метаданные ECMA-335 (`system.reflection.metadata.ecma335`)

| Тип | Строк | Вердикт | Роль |
|---|--:|---|---|
| `MetadataBuilder` (.cs/.Heaps/.Tables) | 3177 | **включить** | ядро: 44 публичных `Add*`, кучи, сериализация таблиц |
| `MetadataRootBuilder` | 129 | **включить** | корень метаданных для PEBuilder |
| `MetadataSizes` | 491 | включить | расчёт размеров стримов |
| `SerializedMetadataHeaps` | 24 | включить | внутренняя структура сериализации куч |
| `CodedIndex` (+tag enums) | 593 | включить | coded indexes всех таблиц |
| `TableIndex` / `HeapIndex` | 82 | включить | enum-ы |
| `BlobDictionary<T>` | 109 | включить | дедупликация блобов |
| `MetadataTokens` | 526 | **включить** | хэндл ↔ runtime token (нужен при эмиссии IL) |
| `Encoding/InstructionEncoder` | 490 | **включить** | высокоуровневый эмиттер инструкций (так пишет Roslyn) |
| `Encoding/MethodBodyStreamEncoder` | 272 | **включить** | поток тел методов в #~ |
| `Encoding/ControlFlowBuilder` | 433 | включить | метки ветвлений, учёт .try/.catch |
| `Encoding/ExceptionRegionEncoder` | 294 | включить | кодирование EH-клаузул |
| `Encoding/BlobEncoders` (custom attribute blobs) | 1392 | включить | кодирование blob-сигнатур CA |
| `Encoding/LabelHandle` (31), `SwitchInstructionEncoder` (40), `MethodBodyAttributes` (22), `FunctionPointerAttributes` (12) | 105 | включить | вспомогательные типы |

Итого папка Encoding = **2986 строк**.

Итого ≈ **8120 строк**.

## 4. PE-запись (`system.reflection.portableexecutable`)

| Тип | Строк | Вердикт | Роль |
|---|--:|---|---|
| `PEBuilder` | 578 | **включить** | каркас раскладки секций, checksum/stamp fixup |
| `ManagedPEBuilder` | 242 | **включить** | managed-раскладка: text section, CorHeader, entrypoint |
| `ManagedTextSection` | 463 | включить | импорт-таблица, IAT, layout text-секции |
| `PEHeaderBuilder` / `PEHeader` | 497 | включить | PE-заголовки |
| `CoffHeader` | 66 | включить | COFF-заголовок |
| `CorHeader` / `CorFlags` | 58 | включить | CLR-заголовок |
| `PEDirectoriesBuilder` | 80 | включить | data directories |
| `SectionHeader` / `SectionLocation` / `DirectoryEntry` | 143 | включить | структуры секций |
| `Machine` / `PEFileFlags` (Characteristics, PEMagic) | 343 | включить | enum-ы |
| `ResourceSectionBuilder` | 19 | **заглушка** | абстрактная база; параметр nullable — не передаём |
| `DebugDirectoryBuilder` (+DebugDirectory/*) | ~700 | **отложить** | передаём null; добавить позже при необходимости |

Итого ≈ **2490 строк** (без DebugDirectory).

## 5. Отсекаемое (read-path / вне PoC)

| Тип | Причина отсечения |
|---|---|
| `PEReader`, `PEHeaders`, `PEBinaryReader`, `PEMemoryBlock`, `PEStreamOptions` (~2000) | read-path; верификация через C#-harness / минимальный reader (итерация N+1) |
| `MetadataReader`, `MetadataReaderProvider`, Decoding/, TypeSystem/, Signatures/, TypeName* (~8000+) | read-path |
| `PortablePdbBuilder`, PortablePdb/ | отсечено по ADR 0009 |
| `EditAndContinueLogEntry`, `EditAndContinueOperation`, EnC-ветки в MetadataSizes/MetadataRootBuilder | отсечено по ADR 0009 |
| `CustomAttributeDecoder`, `SignatureDecoder`, `MetadataAggregator`, `MetadataReaderExtensions`, `ExportedTypeExtensions` | read-хелперы поверх ридера |
| WinMD-ветки | отсечено по ADR 0009 |

## 6. Internal-подмножество (`system.reflection.internal`)

| Тип | Строк | Вердикт |
|---|--:|---|
| `BitArithmetic` | 74 | включить |
| `BlobUtilities` | 340 | включить (запись примитивов little-endian, UTF) |
| `Hash` | 55 | включить (SHA1/SHA256 для BlobContentId) |
| `ExceptionUtilities` | 18 | включить (unreachable) |
| `StreamExtensions` | subset | включить только write-хелперы |

MemoryBlocks/*, ObjectPool, PinnedObject/Buffer, StringUtils, PathUtilities,
DecimalUtilities, CriticalDisposableObject — **не переносим** (read-path или
замещаются kotlin-аналогами).

⚠️ ~~`Hash` использует `System.Security.Cryptography.SHA1/SHA256` — единственная
содержательная BCL-зависимость write-path.~~ **Исправлено (analysis/02 §G):
криптографических зависимостей в write-path нет** — `Internal.Hash` это
FNV-1a/Combine, `BlobContentId` берёт делегат с `Guid.NewGuid()`.

## 7. Итог по объёму переноса

| Группа | Строк C# |
|---|--:|
| Ядро (blobs, хэндлы, IL-enum) | ~3150 |
| Ecma335 (builder + encoding) | ~8120 |
| PE write-path | ~2490 |
| Internal subset | ~490 |
| **Всего к переносу** | **~14 250** |

Уточнение к ADR 0009: оценка выросла с «12–15 тыс.» до ~14 тыс. за счёт
включения папки `Ecma335/Encoding/` (InstructionEncoder, MethodBodyStreamEncoder,
ControlFlowBuilder — ~3000 строк), которая ранее не была учтена.

## 8. Решения (утверждено 2026-08-21)

1. **`Ecma335/Encoding/` включается полностью** (InstructionEncoder,
   MethodBodyStreamEncoder, ControlFlowBuilder, ExceptionRegionEncoder,
   BlobEncoders и вспомогательные типы) — официальный API записи тел
   методов, им пользуется Roslyn.
2. **BlobWriter / BlobWriterImpl включаются** (fixup'ы PEBuilder,
   compressed integers, user strings).
3. **DebugDirectory откладывается** (ManagedPEBuilder принимает null);
   возврат к вопросу на отдельном ревью — см. [I-01](../I-01-metadata-builder.md),
   этап внедрения.
4. **MetadataTokens включается сразу** (нужен compiler-plugin'у при
   эмиссии ldstr/call токенов).
5. **`Hash` (SHA1/SHA256)**: варианты замены `System.Security.Cryptography`
   — (a) тонкая обёртка над `java.security.MessageDigest`, либо
   (b) чисто-Kotlin библиотека [`kotlincrypto/hash`](https://github.com/KotlinCrypto/hash)
   (`org.kotlincrypto.hash:sha1`). Окончательный выбор и критерий
   «чистоты» зависимостей — в Analysis 02 (карта зависимостей).

## 9. Поправка (2026-08-22, по итогам итерации I0)

В исходной инвентаризации был пропущен блок **типизированных хэндлов**:
`Metadata/TypeSystem/Handles.TypeSystem.cs` (2746 строк, ~30 struct'ов —
ModuleDefinitionHandle, TypeDefinitionHandle, MethodDefinitionHandle, …) и
`Metadata/TypeSystem/HandleCollections.TypeSystem.cs` (1941 строка,
handle-коллекции). Это чистые data-структуры, но они необходимы:
`MetadataBuilder.Add*` возвращают типизированные хэндлы.

- Остальное содержимое `TypeSystem/` (AssemblyDefinition.cs,
  MethodDefinition.cs, … ~2900 строк) — read-path row-view'ы, остаётся
  отсечённым.
- `FunctionPointerAttributes` переносится в итерацию BlobEncoders
  (зависит от SignatureAttributes из Internal/MetadataFlags.cs).
- Внутренние константы `Internal/MetadataFlags.cs` (HandleType,
  TokenTypeIds) перенесены в калибровочную итерацию I0.
- Хвост `MetadataFlags.cs` (StringHandleType, HeapHandleType, StringKind,
  TableCounts, HeapSizes) идёт вместе со своими потребителями.

- **Поправка 2 (при выполнении I1):** `HandleCollections.TypeSystem.cs`
  (~1941 строка) — reader-side представления строк таблиц через
  MetadataReader; в write-path не используется → отсечён.

**Итоговый скорректированный объём write-path: ~16.9 тыс. строк C#**
(было ~14.25 тыс. в исходной оценке; пик ~18.9 тыс. до отсечения
коллекций). ADR 0009 («12–15 тыс.») устарел по верхней границе;
суть решения не меняется.
