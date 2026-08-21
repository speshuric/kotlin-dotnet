# Analysis 03: Minimal API surface to port (class-level)

- **Задача:** [I-01](../I-01-metadata-builder.md)
- **Статус:** на утверждении
- **Объём:** типы из analysis/01; видимость и точечные отсечения членов

## 1. Принципы переноса

1. **Видимость**: C# `public` → Kotlin `public` по умолчанию; C#
   `internal` → Kotlin `internal`. Юнит-тесты живут в том же Gradle-модуле
   (`src/test/kotlin`) и видят `internal` — это заменяет механизм
   `InternalsVisibleTo` оригинала.
2. **Именование** (ADR 0009): классы PascalCase как в оригинале; методы и
   свойства — lowerCamelCase; константы и enum-entries — UPPER_SNAKE.
3. **Конструкции**: `static class` → `object`; `readonly struct` для
   хэндлов/токенов → `@JvmInline value class` (над `Int`); encoder-структуры
   → обычные классы; `abstract` + `protected abstract` → напрямую.
4. Публичные члены переносятся все, кроме явно перечисленного в §6.

## 2. Пакет `system.reflection.metadata` (ядро)

| Тип | Видимость C# → Kotlin | Публичный API (группы) |
|---|---|---|
| `BlobBuilder` | public → public | запись примитивов (`WriteByte(s)/UInt16/32/64/Int*/Float/Double/UTF8/CompressedInteger/DateTime/Guid`), резервирование (`ReserveBytes/Reserve*` → `ReservedBlob`), операции чанков (`Count/Truncate/Link`), вывод: `toByteArray()/writeTo(File)` (замена Stream, §6) |
| `BlobWriter` | public → public | примитивы записи в зарезервированную область по offset (`Blob+position`) |
| `BlobWriterImpl` | internal → internal | compressed integer (размер/запись), user string encoding |
| `Blob` | public → public | view над ByteArray: `IsDefault/Length/GetContent()/ToArray()`; `GetReader()/GetPointer/GetComparer` — не переносим (§6) |
| `ReservedBlob<THandle>` | public → public | `CreateWriter()`, `Content`, `Handle` |
| `BlobContentId` | public → public | `(Uuid, stamp)`, парсинг из байтов, `Compute(hashFunction)` с дефолтом random |
| `Handle` | public → public | базовый value-тип токена (`isVirtual` бит), сравнение |
| `EntityHandle` | public → public | конкретный токен метаданных (`Kind`, арифметика token) |
| `HandleKind` (+ext) | public → public | enum типов хэндлов |
| `ILOpCode` | public → public | enum всех опкодов (ushort) |
| `ILOpCodeExtensions` | public → public | размер операнда, кодирование опкода в blob |
| `ExceptionRegion` / `ExceptionRegionKind` | public → public | EH-клаузула (TryOffset…FilterOffset), enum kind |

## 3. Пакет `system.reflection.metadata.ecma335`

| Тип | Видимость C# → Kotlin | Публичный API (группы) |
|---|---|---|
| `MetadataBuilder` | public → public | ~48 `add*` строк таблиц (`AddTypeDefinition`, `AddMethodDefinition`, …), API куч (`AddString/AddBlob/AddUserString/GetUserString…`), `SetCapacity`, `GetRowCounts/GetRowCount`; сериализация — internal (§6: EnC-ветки вырезаны) |
| `MetadataRootBuilder` | public → public | ctor(builder, version, suppressValidation?), `Serialize(builder, methodBodyStreamRva, mappedFieldDataStreamRva)`; EnC/standalone-PDB ветки — вырезаны |
| `MetadataSizes` | public → **internal**¹ | размеры стримов/куч/таблиц, флаги compressed layout |
| `SerializedMetadataHeaps` | internal → internal | результат сериализации куч |
| `BlobDictionary<T>` | internal → internal | словарь дедупликации блобов (FNV-ключи) |
| `CodedIndex` (+7 tag enums) | public → public | кодирование coded index'ов всех таблиц |
| `TableIndex` / `HeapIndex` | public → public | enum-ы таблиц/куч |
| `MetadataTokens` | public → public | object: `TableCount`, token↔handle (`Parse/TryParse`, `GetToken(handle)`), конструкторы хэндлов по номеру строки |
| `Encoding/InstructionEncoder` | public → public | `OpCode(ILOpCode)`, branch/switch через `LabelHandle`, `DefineLabel/MarkLabel`, вызовы/аргументы токенами |
| `Encoding/MethodBodyStreamEncoder` | public → public | `AddMethodBody(...)` → смещение в потоке тел методов |
| `Encoding/ControlFlowBuilder` | public → public | builder ветвлений и EH-регионов для InstructionEncoder |
| `Encoding/ExceptionRegionEncoder` | public → public | добавление try/catch/filter/finally клаузул |
| `Encoding/LabelHandle`, `SwitchInstructionEncoder`, `MethodBodyAttributes`, `FunctionPointerAttributes` | public → public | вспомогательные |
| `Encoding/BlobEncoders` (24 struct'а) | public → public² | fluent-кодировщики сигнатур и custom attribute блобов (`SignatureTypeEncoder`, `MethodSignatureEncoder`, `LiteralEncoder`, …) |

¹ Отклонение от оригинала: в C# класс public, но потребители — только
внутренние пути сериализации и тесты. В Kotlin делаем `internal`
(тесты того же модуля видят).
² Вопрос на утверждение: можно отложить на позднюю итерацию, если
первый EXE обойдётся без custom attribute/signature-энкодеров.

## 4. Пакет `system.reflection.portableexecutable`

| Тип | Видимость C# → Kotlin | Публичный API (группы) |
|---|---|---|
| `PEBuilder` | public abstract → public abstract | ctor(name, deterministicId), `serialize(): ByteArray` / `writeTo(File)` (§6), protected `createSections()/serializeSection(...)` |
| `PEBuilder.Section` | public → top-level data class³ | (name, flags) |
| `ManagedPEBuilder` | public sealed → public class | ctor(header, metadataRootBuilder, ilStream, mappedFieldData?, managedResources?, nativeResources?, strongNameSignatureSize=…); debugDirectoryBuilder — параметр отсутствует до ревью (§6) |
| `ManagedTextSection` | public → public | расчёт размеров text-секции (IAT, CorHeader, strongname), `serialize(builder, relocationStartOffset)` |
| `PEHeaderBuilder` | public → public | фабрики заголовков (`CreateDefault`…) |
| `PEHeader` / `CoffHeader` | public → public | структуры заголовков + `WriteTo` |
| `CorHeader` / `CorFlags` | public → public | CLR-заголовок, enum флагов |
| `PEDirectoriesBuilder` | public → public | data directories |
| `SectionHeader` / `SectionLocation` / `DirectoryEntry` | public → public | структуры секций |
| `Machine` / `Characteristics` / `PEMagic` (PEFileFlags.cs) | public → public | enum-ы |
| `ResourceSectionBuilder` | public abstract → public abstract | база native resources (~20 строк, параметр nullable) |

³ Отклонение: вложенную структуру делаем top-level (котлиновская норма).

## 5. Пакет `system.reflection.internal`

| Тип | Видимость | Примечание |
|---|---|---|
| `BitArithmetic` | internal object | округления/деления размеров |
| `BlobUtilities` | internal object | LE-примитивы, guid reorder (см. analysis/02 §E), UTF-8 счётчик/писатель |
| `Hash` | internal object | FNV-1a, Combine |
| `ExceptionUtilities` | internal object | unreachable |
| `StreamExtensions` | internal object (subset) | только write-хелперы; при memory-output может исчезнуть вовсе |

## 6. Точечные отсечения членов (относительно оригинала)

| Член | Причина |
|---|---|
| Все параметры/перегрузки со `Stream`: `BlobBuilder.WriteContentTo(Stream)`, `TryWriteBytes(Stream,…)`; `PEBuilder.Serialize(Stream)` | решение analysis/02 §8.1: память→файл; замена `toByteArray()/writeTo(File)` |
| `Blob.GetReader()`, `Blob.GetPointer()`, `Blob.GetComparer()` | тянут BlobReader/MemoryBlock (read-path); дедупликация куч использует ordinal-сравнение без них |
| `debugDirectoryBuilder` параметр `ManagedPEBuilder` | DebugDirectory отложен (analysis/01 §8.3, I-01 «Открытые вопросы») |
| EnC-ветки: `IsEncDelta`, standalone-debug-metadata в `MetadataSizes`/`MetadataRootBuilder.Serialize`/`SerializeMetadataHeader` | ADR 0009 п.5 |
| WinMD/projection-ветки заголовков | ADR 0009 п.5 |

## 7. Итог

Публичная поверхность: **~40 публичных типов** + ~10 внутренних.
Крупнейшие блоки API: `MetadataBuilder` (~55 методов), `BlobBuilder`/
`BlobWriter` (~100 методов записи), `MetadataTokens` (~56 членов),
encoder-флей BlobEncoders (~170 методов).

## 8. Решения (утверждено 2026-08-22)

1. Видимость по §1 — утверждено.
2. Отклонение ¹: `MetadataSizes` → Kotlin `internal` — утверждено.
3. Отклонение ³: `Section` — top-level data class — утверждено
   (мотивация: котлиновская норма, меньше шума в сигнатурах наследника).
4. **Общая политика**: C# `struct` обычно → Kotlin `data class`
   (value class — только для хэндлов/токенов над одним Int, §1.3).
5. `BlobEncoders` — переносится полностью, последней итерацией блока
   Ecma335.
