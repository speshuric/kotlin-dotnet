# Сверка тестов: dotnet/runtime SRM ↔ dotnetutils

Дата: 2026-08-22. База апстрима: `release/10.0`, коммит
`4a4758eb06bc1fa42fb69442af63f30026a23c9e` (см. ADR 0009/0011).

## Сводка

| | dotnet/runtime | dotnetutils |
|---|--:|--:|
| тестовых файлов | 38 (вкл. хелперы без фактов) | 19 |
| `[Fact]`+`[Theory]` деклараций | ~515 | — |
| исполняемых кейсов | больше деклараций (Theory/MemberData разворачиваются) | **242** `@Test` |

Сопоставление ведётся по файлам/фактам; часть наших `@Test` объединяет
несколько C#-фактов в циклы, поэтому прямое равенство чисел не является
целью — цель: каждый *факт поведения* либо перенесён, либо осознанно
отсечён с причиной.

Статусы: ✅ перенесён · 🟡 частично · ❌ не перенесён (причина) ·
➕ наш, без прямого аналога

## Metadata/ (апстрим)

| Файл | Фактов | Наш файл | Тестов | Статус / остаток |
|---|--:|---|--:|---|
| BlobTests.cs | 41 | BlobCoreTests + **BlobBuilderTests** | 20+29 | ✅ закрыт (Stream/byte*/Pooled/ImmutableArray/DateTime/Decimal/UTF8-substring отсечены — шапка файла) |
| BlobContentIdTests.cs | 4 | BlobCoreTests (`contentId_*`) | 4 | 🟡 ~2/4; остаток — низкий приоритет |
| BlobUtilitiesTests.cs | 1 | BlobCoreTests (+UTF8-факты из BlobTests) | — | ✅ |
| HandleTests.cs | 12 | HandleTests + TypedHandleTests | 6+8 | 🟡 ядро конверсий/kind-check'ов покрыто; часть фактов отсечена вместе с ToDebugInformationHandle и reader-хэндлами (I1) |
| HandleComparerTests.cs | 2 | — | 0 | ❌ HandleComparer не портирован (не нужен write-path) |
| LargeTablesAndHeapsTests.cs | 32 | — | 0 | ❌ отложено по плану (большие кучи >2^28 — не-goal PoC) |
| MetadataReaderProviderTests.cs | 7 | — | 0 | ❌ провайдеры/стримы не портированы (ByteArray-only, ADR 0011) |
| MetadataReaderTests.cs | 61 | RoundTripTests (замещающий подход) | 5 | 🟡 полный порт отложен до расширения reader'а; ключевые инварианты покрывает round-trip writer→reader |
| TagToTokenTests.cs | data-driven | — | 0 | 🟡 покрывается round-trip'ом неявно |
| AssemblyNameInfoTests.cs | 10 | — | 0 | ❌ вне скопа порта |
| TypeName*.cs (3 файла) | 37 | — | 0 | ❌ вне скопа порта |
| MetadataReaderTestHelpers.cs | хелпер | — | — | N/A |

## Ecma335/

| Файл | Фактов | Наш файл | Тестов | Статус / остаток |
|---|--:|---|--:|---|
| BlobEncodersTests.cs | 63 | BlobEncodersTests.kt | 43 | 🟡 все структурные факты перенесены; кейсы с невалидными raw-значениями enum'ов невоспроизводимы в Kotlin enum'ах (шапка файла) |
| CodedIndexTests.cs | 15 | CodedIndexAndTokensTests | 14 | 🟡 векторный стиль адаптирован; reflection-часть TagToToken непереносима |
| MetadataTokensTests.cs | 8 | ↳ тот же файл | ↳ | ✅ (слит) |
| ControlFlowBuilderTests.cs | 11 | ControlFlowBuilderTests.kt | 11 | ✅ |
| ExceptionRegionEncoderTests.cs | 6 | ExceptionRegionEncoderTests.kt | 8 | ✅ (+2 наших граничных) |
| InstructionEncoderTests.cs | 17 | InstructionEncoderTests.kt | 17 | ✅ |
| LabelHandleTests.cs | 1 | — | 0 | ❌ тривиальное равенство value class; отмечено как долг мелочей |
| MethodBodyStreamEncoderTests.cs | 15 | MethodBodyStreamEncoderTests.kt | 15 | ✅ |
| MetadataBuilderTests.cs | 25 | MetadataBuilderTests (20) + MetadataBuilderAddTests (11) | 31 | ✅ (разделён на два файла; validation-order факты через validateOrder) |
| MetadataRootBuilderTests.cs | 8 | MetadataRootBuilderTests.kt | 9 | ✅ (+1 наш) |
| MetadataAggregatorTests.cs | 2 | — | 0 | ❌ EnC-скоуп отсечён |
| PortablePdbBuilderTests.cs | 4 | — | 0 | ❌ PDB отсечён (ADR 0009) |

## Utilities/

| Файл | Фактов | Наш файл | Статус |
|---|--:|---|---|
| CompressedIntegerTests.cs | 8 | BlobReaderTests.kt | ✅ полный порт (спек-байты II.23.2, границы, invalid) |
| BlobReaderTests.cs | 8 | ↳ тот же файл | 🟡 переносимый поднабор (примитивы/границы); pointer-plumbing неприменим |
| HashTests.cs | 5 | — | 0 | ❌ FNV/Combine не покрыты (Hash портирован в I2 без тестов) — мелкий долг |
| AbstractMemoryBlockTests / MemoryBlockTests | 14 | — | ❌ MemoryBlock не портирован (ByteArray) |
| OrderByTests.cs | 2 | — | ❌ внутренняя сортировка EnC-хелпера |
| StreamExtensionsTests.cs | 3 | — | ❌ Stream |
| StringUtilsTests.cs | 1 | — | ❌ |

## PortableExecutable/

| Файл | Фактов | Наш файл | Тестов | Статус / остаток |
|---|--:|---|--:|---|
| PEBuilderTests.cs | 11 | PEBuilderTests.kt | 8 | 🟡 GetContentToSign×3, prefix/suffix, NativeResources, ошибки ctor; BasicValidation/Complex/RSA/checksum-FX → C#-harness (S5) |
| PEHeaderBuilderTests.cs | 2 | PEHeaderBuilderTests.kt | 3 | ✅ (+наш computeSizeOfPEHeaders) |
| SectionHeaderTests.cs | 1 | — | 0 | 🟡 покрыт косвенно round-trip'ом/NativeResources |
| PEBinaryReaderTests.cs | 2 | — | 0 | 🟡 покрыт косвенно BlobReader/PEReader |
| PEHeadersTests.cs | 8 | — | 0 | 🟡 подмножество через PEReader-lite + round-trip |
| PEReaderTests.cs | 35 | — | 0 | ❌ reader-обвязка (stream options, bad images) вне минимального I8; частично замещено RoundTrip |
| PEMemoryBlockTests.cs | 6 | — | 0 | ❌ MemoryBlock |
| DebugDirectoryBuilderTests / DebugDirectoryTests | 26 | — | 0 | ❌ DebugDirectory отсечён (roadmap §D) |
| BadImageFormat.cs | 1 | — | 0 | 🟡 неявно: битые образы кидают ISE в PEReader-lite |

## Наши тесты ➕ без прямого аналога в апстриме

| Наш файл | Что проверяет | Почему придуман |
|---|---|---|
| reader/RoundTripTests.kt (5) | writer→reader round-trip: compressed ints, строки таблиц, IL/user-string через PE | ADR 0011: плотная сетка fidelity; у SRM writer и reader тестируются раздельно |
| portableexecutable/HelloWorldImageTests.kt | дым M1: образ собирается, детерминирован, DOS/PE сигнатуры | артефакт майлстона M1 |
| ./PortableExecutableEnumsTests.kt (4) | калибровка I0: значения Machine/CorFlags/PEMagic | калибровочная итерация порта |
| ecma335/MetadataBuilderAddTests.kt — часть | row-count стиль добавления строк | адаптация под наш split-файл |
| BlobBuilderTests/BlobReaderTests — отдельные кейсы | регрессия U+FFFD-бага, Kotlin-специфика (IllegalArgumentException вместо sentinel) | локальные фиксы |

## Почему ❌-группы отложены (аргументация)

### LargeTablesAndHeapsTests — отложено, не вырезано
Проверяют переход writer'а через малые форматы индексов: строки таблиц
>65535 (coded index не влезает в u16) и кучи >64КБ (флаг `HeapSizeFlag`
→ 4-байтовые ссылки). Апстрим строит для этого метаданные с миллионами
строк — тяжёлые и медленные тесты.

**Код портирован и жив**: `MetadataSizes` считает `*IsSmall` флаги для
всех семейств индексов; ветка больших форматов реализована, но ни разу
не выполнялась в тестах. PoC-сборки имеют ~десятки строк.

**Триггер возврата**: этап 6 (bootstrapping runtime на Kotlin) или любая
сборка, приближающаяся к 65k строк / куче 64КБ. Тогда закрывать через
форсирование флагов через внутренний шов, а не миллионами строк.

### TypeName*/AssemblyNameInfo (~47 фактов) — вне скоупа
Read-side API парсинга строк `"Ns.Type, Assembly, Version=..."`
(сценарии замены BinaryFormatter). К записи сборок отношения не имеет:
в пайплайне нет ни одного места, где парсится имя типа из строки.
ADR 0009 зафиксировал скоп — write-path only. Появится только при
добавлении функциональности, потребляющей такие строки.

### HandleComparer (2 факта) — не нужен
Обёртка `IComparer/IEqualityComparer` над хендлами для сортировки
коллекций (EnC/агрегатор). Сортировка таблиц у нас идёт по вычисленному
целому coded index внутри `MetadataBuilder`. Равенство хендлов —
семантика value class'а, покрытая TypedHandleTests.

## Вне сравнения (другие слои проекта)

- compiler-plugin: контрактные тесты IlEmitter + e2e `just test`
  (00-int-add … 05-pe-hello, dual-backend S5) — это слой интеграции,
  у апстрима аналогов нет.
- C#-harness `kotlin-dotnet-utils/verifier` — независимый оракул,
  остаётся финальной проверкой pe-артефактов (ADR 0011).

## Итог

- ✅ полных: Blob*, CompressedInteger, ControlFlow/Instruction/
  MethodBodyStream энкодеры, MetadataBuilder(+Root), PEHeaderBuilder,
  PEBuilder (переносимая часть)
- 🟡 частичных: Handle(s), CodedIndex/Tokens, BlobEncoders (enum-кейсы),
  BlobContentId, PEBuilder (runtime-cases → harness), MetadataReader
  (замещён round-trip)
- ❌ осознанно не перенесены: LargeTables, WinMD/PDB/EnC/Aggregator,
  TypeName*/AssemblyNameInfo, провайдеры/Stream/MemoryBlock,
  DebugDirectory×26, PEReader-обвязка, HandleComparer, HashTests,
  LabelHandle, SectionHeader (косвенно)
