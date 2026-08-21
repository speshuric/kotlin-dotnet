# Analysis 02: Dependency map C# → Kotlin (write-path)

- **Задача:** [I-01](../I-01-metadata-builder.md)
- **Статус:** на утверждении
- **Область анализа:** файлы из утверждённого объёма (analysis/01)

## Метод

Просмотрены `using`-директивы и типы BCL во всех файлах write-path из
analysis/01. Ниже — каждая зависимость, места использования и способ
обработки при переносе.

## A. Коллекции — прямые аналоги в kotlin.collections

| C# | Где | Замена |
|---|---|---|
| `ImmutableArray<T>` | публичный API: `BlobBuilder.ToImmutableArray/WriteBytes`, `MetadataSizes.{HeapSizes,RowCounts,ExternalRowCounts}`, секции `PEBuilder`, параметры `BlobEncoders` | `List<T>` (чтение) / `MutableList<T>` (накопление) |
| `ImmutableCollectionsMarshal.AsImmutableArray` | BlobBuilder:318 | не нужен — возвращаем сам `ByteArray` |
| `Dictionary<,>` / `HashSet<>` / `List<>` | MetadataBuilder.Heaps, ControlFlowBuilder, BlobDictionary | `MutableMap` / `MutableSet` / `MutableList` |

Сложность: **trivial**. Отдельный тип под ImmutableArray не заводим
(ADR 0009, п.4 принципов).

## B. Байтовые примитивы / unsafe — ручная замена

| C# | Где | Замена |
|---|---|---|
| `Unsafe.WriteUnaligned` | BlobUtilities (все записи LE) | ручной цикл по байтам в `ByteArray` |
| `MemoryMarshal.*` | 3 использования (BlobBuilder, BlobWriter, MetadataBuilder.Heaps) | то же |
| `BinaryPrimitives.ReverseEndianness` | BlobUtilities (big-endian ветка) | не нужна: пишем только LE вручную |
| `BitConverter.DoubleToUInt64Bits/SingleToUInt32Bits` | BlobUtilities (double/float) | `Double.toRawBits()` / `Float.toRawBits()` — **есть в kotlin-stdlib** |
| `unsafe`-блоки, указатели (`char*`, `byte*`) | BlobBuilder.WriteUTF8, BlobUtilities | исчезают: индексы по `String`/`ByteArray` |

Сложность: **easy**, но требует аккуратности (это самый горячий путь).

## C. Текст / UTF-8 — уже «чистый» код

- UTF-8 в write-path кодируется **собственным арифметическим кодировщиком**
  (`BlobUtilities.GetUTF8ByteCount/WriteUTF8`), а не `System.Text.Encoding`
  → переносится напрямую, без java.nio.charset.
- `EndsWith(..., StringComparison.Ordinal)` → котлиновский `endsWith`
  (ordinal по умолчанию).
- Сложность: **easy**.

## D. Stream / вывод файла — требуется решение

Места: `BlobBuilder.WriteContentTo(Stream)`,
`BlobBuilder.TryWriteBytes(Stream, int)`, `PEBuilder.Serialize(Stream)`.

Варианты:
- **(a) память → файл** (рекомендуется): сериализация целиком в
  `ByteArray`/`BlobBuilder`; публичный API — вернуть `ByteArray` и/или
  записать по пути средствами `kotlin.io` (`File.writeBytes`). Размеры
  наших сборок — килобайты; seekable-запись не нужна, PEBuilder
  раскладывает секции в буфере так же, как в стрим.
- (b) минимальный интерфейс-приёмник (seekable output) — сделать позже,
  если появится потребитель больших сборок/потоков.

Сложность: **trivial** при варианте (a); прямой аналог `java.io.Stream`
не заводим (принцип «без import java.*»).

## E. Guid — kotlin.uuid.Uuid (решение утверждено)

Места: `BlobContentId` (поле Guid, `Guid.NewGuid()` как дефолтная
hash-функция), метаданные (MVID).

Решение: **`kotlin.uuid.Uuid`** с opt-in
`@OptIn(ExperimentalUuidApi::class)` (зафиксировать opt-in в
build.gradle.kts модуля).

⚠️ **Проверено: сериализация НЕ совпадает «из коробки»** — обязательное
требование к реализации:
- SRM пишет guid через `Guid.TryWriteBytes` — mixed-endian layout .NET:
  `int32 LE | int16 LE | int16 LE | 8 raw bytes`;
- `kotlin.uuid.Uuid` следует RFC-4122 (big-endian msb/lsb,
  на JVM — делегирует java.util.UUID);
- поэтому порт `BlobUtilities.WriteGuid(Uuid)` обязан явно переставлять
  байты из `mostSignificantBits()`/`leastSignificantBits()`
  (`b3 b2 b1 b0 | b5 b4 | b7 b6 | lsb…`);
- верификация: golden-тест — guid, записанный нашим кодом, должен быть
  побайтово равен записи того же значения через C# `SRM`
  (сверка в C#-harness).

## F. Diagnostics / атрибуты контрактов — отбрасывается

- `Debug.Assert(cond)` → `require`/`check` (или omit в горячем пути);
  `Debug.Fail` → `error(...)`.
- `[NotNull]`, `[return: NotNullIfNotNull]`, `[MemberNotNull...]`
  (`System.Diagnostics.CodeAnalysis`) → не переносятся, nullability
  выражается типами Kotlin.

Сложность: **trivial**.

## G. Криптография — зависимости НЕТ (корректировка Analysis 01)

Утверждение analysis/01 §6 («Hash использует SHA1/SHA256») неверно:
`Internal.Hash` — только FNV-1a и Combine (целочисленное хэширование
для словарей). Поиск `System.Security.Cryptography` по всему src SRM —
**0 попаданий**. `BlobContentId.Compute` принимает делегат
`hashFunction: () -> BlobContentId` (дефолт: `Guid.NewGuid()` +
timestamp). Вопрос обёртки над `MessageDigest`/kotlincrypto **снимается**.

## H. Пулинг / многопоточность — не переносится

`ObjectPool<T>`, `PooledBlobBuilder`, `ArrayPool`, lock'и в write-path
отсутствуют либо относятся к read-path. Переносим без пулов
(ADR 0009, п.6).

## I. Прочее мелкое

| C# | Замена |
|---|---|
| `IDisposable` у BlobBuilder (для пулинга) | не нужен в непуленной версии |
| `ref struct`/`Span<byte>` параметров | `ByteArray + offset + length` |
| `Math.DivRem` и пр. | ручная реализация в BitArithmetic |

## Итоговая таблица решений

| Зависимость | Решение | Сложность |
|---|---|---|
| ImmutableArray | `List`/`MutableList` | trivial |
| Unsafe/MemoryMarshal/BinaryPrimitives | ручная запись байтов | easy |
| BitConverter bits | `toRawBits()` stdlib | trivial |
| UTF-8 | перенос собственного кодировщика | easy |
| Stream → вывод | (a) память → файл через kotlin.io | trivial |
| Guid | `kotlin.uuid.Uuid` (opt-in) | easy |
| Debug.Assert/аннотации | require/check/error; отбросить атрибуты | trivial |
| Криптография | отсутствует — вопрос снят | — |
| Пулинг/потоки | не переносится | — |

## 8. Решения (утверждено 2026-08-21)

1. **Вывод сборки: вариант (a)** — сериализация в память + запись файла
   через `kotlin.io`; пометить как **временное решение** при реализации
   (TODO: sink-интерфейс, если появится потребитель потоков/больших
   сборок).
2. **Guid: `kotlin.uuid.Uuid`** с opt-in на
   `@ExperimentalUuidApi`. Сериализация guid'а отличается от .NET
   (RFC-4122 big-endian vs mixed-endian) — см. §E: явный reorder байтов
   в WriteGuid + golden-тест против C#-вывода обязательны.
