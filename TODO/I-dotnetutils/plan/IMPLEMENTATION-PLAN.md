# Implementation Plan: org.kotlindotnet.dotnetutils.system.reflection.metadata

- **Задача:** [I-01](../I-01-metadata-builder.md)
- **Статус:** на утверждении
- **Вход:** analysis/01–04 (объём ~14 250 строк C#, ~50 типов)

## Принципы плана

1. **Порядок работ внутри каждой итерации** (фиксированный):
   1. перенос публичных сигнатур блока (тела = `TODO(...)`) — модуль компилируется;
   2. перенос/адаптация тестов блока из dotnet/runtime — красные;
   3. функциональная реализация — зелёные;
   4. коммит логически целостного блока (по согласованию).
2. **Тесты** берутся из оригинала (`tests/Metadata`, `tests/PortableExecutable`)
   с переводом xunit→kotlin.test/JUnit5; проверки через internal-API идут
   напрямую (Kotlin `internal` виден тестам того же модуля).
3. **Итерация ≤ 10 тыс. строк C#** (фактически 1.4–4.5 тыс.), блок =
   независимая единица коммита и параллелизации агентов.
4. Отклонения от оригинала фиксируются в шапке соответствующего файла
   (см. analysis/03 §6 — Stream, EnC, DebugDirectory…).

## Инфраструктура тестирования (создаётся до Iteration 1)

- **JUnit**: подключить `kotlin("test")` (kotlin-test-junit5) в
  `dotnetutils/build.gradle.kts`; opt-in `ExperimentalUuidApi` там же.
- **C#-harness** (ground truth): маленький консольный проект на настоящем
  SRM, расположение — `<repo>/verifier/` (рядом с `runtime/`):
  открывает наш DLL/EXE через `PEReader`/`MetadataReader`, ассертит
  таблицы/кучи/заголовки; запускается из Gradle-task или скрипта.
  Нужен начиная с Iteration 3 (первая сериализация метаданных).
- **Golden-юнит-тесты**: только для фиксированных байтовых областей
  (guid layout, заголовки, compressed integers) — см. analysis/04 §Критерий.

## Итерации

| # | Блок | Строк C# | Зависит от | Ключевые тесты оригинала |
|---|---|--:|---|---|
| I0 | **Калибровочная: enum'ы и простые структуры** — `ILOpCode`, `TableIndex`, `HeapIndex`, `HandleKind`, `ExceptionRegionKind`, `MethodBodyAttributes`, `FunctionPointerAttributes`, `CorFlags`, `Machine`, `Characteristics/PEMagic`; value/data классы: `Handle`, `EntityHandle`, `LabelHandle`, `ExceptionRegion`, `SectionLocation`, `DirectoryEntry` | ~830 | — | `HandleTests` |
| I1 | Internal utils + Blob-ядро: `BitArithmetic`, `BlobUtilities`, `Hash`, `ExceptionUtilities`, `Blob`, `BlobBuilder`(+Enum), `ReservedBlob`, `BlobWriter`, `BlobWriterImpl`, `BlobContentId` | ~2820 | I0 (частично: Uuid/guid-типы) | `BlobTests`, `BlobUtilitiesTests`, `BlobContentIdTests` |
| I2 | Токены и IL: `CodedIndex`(+tags), `MetadataTokens`, `ILOpCodeExtensions` | ~1070 | I0 | `TagToTokenTests`, `CodedIndexTests`, `MetadataTokensTests` |
| I3 | Ядро метаданных: `MetadataSizes`, `SerializedMetadataHeaps`, `BlobDictionary`, `MetadataBuilder`(×3 файла), `MetadataRootBuilder` | ~4530 | I1, I2 | `MetadataBuilderTests`, `MetadataRootBuilderTests`, `LargeTablesAndHeapsTests`¹ |
| I4 | Encoding (без BlobEncoders): `InstructionEncoder`, `MethodBodyStreamEncoder`, `ControlFlowBuilder`, `ExceptionRegionEncoder`, `SwitchInstructionEncoder` | ~1560 | I3 | тесты Encoding из `tests/Metadata/Ecma335` |
| I5 | PE write-path: `PEHeader(+Builder)`, `CoffHeader`, `CorHeader`, `PEDirectoriesBuilder`, `SectionHeader`, `ManagedTextSection`, `PEBuilder`(+Section), `ManagedPEBuilder`, `ResourceSectionBuilder`² | ~2360 | I1, I3 | `PEHeaderBuilderTests`, `PEBuilderTests`³ |
| **M1** | **Майлстон: первый EXE целиком Kotlin-кодом** | — | I1–I5 | C#-harness + `dotnet-ildasm` + e2e |
| I6 | `Encoding/BlobEncoders` (24 encoder-структуры) | ~1390 | I1, I3 | тесты custom attribute encoding |
| I7 | (отдельное решение) минимальный `MetadataReader` для самопроверки | ~3000–4000 | M1 | родные reader-тесты⁴ |

**I0 — цель:** отладить конвертацию на материале без функциональной
нагрузки: соглашения именования (PascalCase классов, lowerCamelCase
членов, UPPER_SNAKE enum-entries), маппинг `static enum`→Kotlin `enum`,
`readonly struct`→`@JvmInline value class`/`data class`, wiring
kotlin-test/JUnit5 в модуле. Результат — зафиксированные конвенции на
живом коде + зелёные `HandleTests`.

¹ `LargeTablesAndHeapsTests` — частично (большие кучи не приоритет PoC).
² Заглушка: protected abstract serialize без реализаций native resources.
³ Адаптация: факты через PEReader заменяются на C#-harness; часть
  пропускается (analysis/01 §«Верификация»).
⁴ Решение о I7 принимается после M1 (analysis/01 §8 / ADR 0009).

## Майлстон M1 — критерии приёмки

EXE «hello world», собранный Kotlin-кодом модуля (без ilasm):
1. C#-harness открывает сборку настоящим SRM — ошибок нет;
2. запуск через `dotnet` даёт ожидаемый вывод;
3. `dotnet-ildasm` показывает корректные IL/метаданные
   (эквивалентность вместо побайтового совпадения — analysis/04);
4. интеграция как экспериментальный путь в test-infra (recipe рядом с
   существующим ilasm-путём; переключение compiler-plugin — отдельная
   задача после стабилизации).

## Параллелизация (агенты с ограниченным контекстом)

- Секвенция зависимостей: I0 → I1 → I2 → I3 → I4 → I5 → M1; I6 может идти
  параллельно с I4/I5 (нужны только I1+I3).
- Внутри итерации параллелятся: перенос сигнатур ↔ перенос тестов
  (разные файлы), затем мясо одним агентом (чтобы не спорить за тела).
- Каждый агент получает: этот план + analysis/03 (API-surface своего
  блока) + пути исходников/тестов оригинала. Полный контекст проекта
  не нужен (практика TODO №2).

## Трекинг

Статус итераций ведётся прямо в таблице выше (`TODO`/`WIP`/`DONE`
в колонке «Блок» не заводим — статусы в чеклистах ниже по мере старта
работ; при утверждении плана все итерации `TODO`).
