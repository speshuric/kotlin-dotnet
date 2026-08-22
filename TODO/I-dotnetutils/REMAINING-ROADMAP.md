# Оставшийся roadmap порта dotnetutils и внедрения

Этот файл — единый трекинг оставшейся работы после сессии 2026-08-22
(I0–I5 выполнены и закоммичены; см. IMPLEMENTATION-PLAN.md для
истории). Пункты идут в рекомендуемом порядке выполнения.

## Upstream pin (база порта)

Порт выполнялся от dotnet/runtime `release/10.0`, коммит
`4a4758eb06bc1fa42fb69442af63f30026a23c9e` (2026-08-19), путь
`src/libraries/System.Reflection.Metadata/src/`. Дублируется в
ADR 0009 и README модуля (`kotlin-dotnet-engine/dotnetutils/README.md`).
Проверка обновлений: дифф апстрима от этого коммита, перенос дельты
write-path в порт.

## A. Тесты: добивка непортированных фактов

| Файл | Фактов C# | Сейчас | Осталось | Приоритет |
|---|--:|--:|--:|---|
| BlobTests.cs | 41 | 41 (BlobCore+BlobBuilder) | 0 — закрыто | ✅ |
| BlobUtilitiesTests.cs | 1 | ~3 + UTF8-факты из BlobTests | добивка минимальна | ✅ |
| BlobContentIdTests.cs | 4 | ~2 | ~2 (низкий, отложено) | низкий |
| LargeTablesAndHeapsTests.cs | ? | 0 | частично (большие кучи не приоритет PoC) | отложено |

Заметки:
- BlobTests — самый крупный пробел: golden bytes для примитивов,
  LinkPrefix/LinkSuffix композиция, TryGetSpan, chunk management,
  contentEquals на многочанковых билдерах, WriteConstant все типы.
- BlobUtilitiesTests: GetUTF8ByteCount boundary + GetUserStringTrailingByte
  уже покрыты косвенно через BlobCoreTests, но нужен прямой порт.
- Все тесты верифицируются без MetadataReader (только writer side).

## B. Итерации порта (из IMPLEMENTATION-PLAN.md)

### B.1. I6: PE write-path (~2360 строк C#) — **[DONE] 2026-08-22**

Файлы: `PEHeader(+Builder)`, `CoffHeader`, `CorHeader`,
`PEDirectoriesBuilder`, `SectionHeader`, `ManagedTextSection`,
`PEBuilder`(+Section), `ManagedPEBuilder`, `ResourceSectionBuilder`.

Порт выполнен в `...system/reflection/portableexecutable/`. Ключевые
отклонения (в шапках файлов): [Flags]-enum'ы → raw Int/UInt;
DebugDirectoryBuilder отсечён (параметр `debugDirectoryBuilder` у
ManagedPEBuilder отсутствует, debugDataSize всегда 0); time-based
content id provider берёт время из `PEBuilder.TIME_PROVIDER`
(по умолчанию 0; pure-Kotlin stdlib без wall-clock); C#
`protected internal` ResourceSectionBuilder.Serialize → Kotlin
`internal abstract`.

Тесты: 11 новых (PEHeaderBuilderTests, PEBuilderTests) —
переносимое подмножество PEHeaderBuilderTests + PEBuilderTests
(GetContentToSign×3, GetPrefixBlob/GetSuffixBlob, NativeResources
через парсинг сырого image, ошибки ctor). PEReader/MetadataReader
и RSA-signing тесты отложены на M1 (harness). Итого в модуле 154
теста, все зелёные.

Блокирует M1.

### B.2. M1: Майлстон — первый EXE целиком Kotlin'ом — **[DONE] 2026-08-22**

Критерии приёмки выполнены (тест `05-pe-hello`:
`scripts/build-test.sh 05-pe-hello`, `just test 05-pe-hello`; C#-harness
в `kotlin-dotnet-utils/verifier/`):
1. EXE «hello world» собран чисто Kotlin-кодом модуля
   (тест-фикстура HelloWorldImage в test-источниках dotnetutils:
   MetadataBuilder + MethodBodyStreamEncoder + InstructionEncoder +
   ManagedPEBuilder, сигнатурные блобы собраны руками до I7);
2. C#-harness `kotlin-dotnet-utils/verifier/` открывает сборку
   настоящим SRM — VERIFIER OK;
3. `dotnet hello.exe` печатает ожидаемый вывод;
4. `dotnet-ildasm` декомпилирует образ.

Найденная и зафиксированная ловушка: MethodAttributes.Static = 0x0010
(не входит в access-mask); entry point без Static даёт
TypeLoadException "The signature is incorrect". Recipe:
runtimeconfig.json генерируется скриптом (конфигурация хоста .NET, не
часть PE-образа).

### B.3. I7: BlobEncoders (24 encoder-структуры, ~1390 строк) — **[DONE] 2026-08-22**

Порт `Ecma335/Encoding/BlobEncoders.cs` + `Signatures/Signature*.cs`
(enum'ы SignatureKind/Attributes/CallingConvention/TypeCode,
SerializationTypeCode, PrimitiveTypeCode, CustomAttributeNamedArgumentKind)
+ `FunctionPointerAttributes.cs` + `SignatureHeader.cs` — в
`...metadata/ecma335/` (`SignatureTypes.kt`, `SignatureHeader.kt`,
`BlobEncoders.kt`).

Отклонения: C# `out`-overloads → lambda-callbacks; методы, совпадающие
с ключевыми словами Kotlin, переименованы (`Enum`→`enumType`,
`Object`→`objectType`, `Array`→`array`); ImmutableArray → IntArray?
(null = «все lower bounds 0»); FunctionPointerAttributes значения
выписаны явно.

Тесты: 43 новых (BlobEncodersTests) — golden-bytes порт
BlobEncodersTests.cs; enum-кейсы C# с невалидными raw-значениями
не воспроизводимы в Kotlin enum'ах (отмечено в тестах). Итого в модуле
198 тестов, все зелёные. Может идти после M1: HelloWorldImage теперь
может заменить рукописные сигнатуры на BlobEncoder'ы.

### B.4. I8: Минимальный MetadataReader — **[DONE] 2026-08-22** (вариант B, ADR 0011)

Портировано в `...metadata/reader/` + `BlobReader`:
- BlobReader (compressed unsigned/signed — точное зеркало SRM
  PeekCompressedInteger/TryReadCompressedSignedInteger, UTF8/UTF16,
  примитивы);
- MetadataReader: парс root/streams, кучи #Strings/#US/#Blob/#Guid,
  строки наших 11 таблиц (Module, TypeRef, TypeDef, Field, MethodDef,
  Param, MemberRef, StandAloneSig, TypeSpec, Assembly, AssemblyRef);
- PEReader-lite: DOS/PE/COFF/optional header + section map
  (rva→offset) + MethodBody (tiny/fat, exception regions small/fat).

Найдено при отладке (зафиксировано):
- coded index widths: ResolutionScope и TypeDefOrRef — **2 бита**
  (4 и 3 тега), MemberRefParent — 3 бита (5 тегов), II.24.2.6;
- TypeDef/TypeRef физический порядок полей: Name затем Namespace;
- compressed signed: знак — LSB всего raw-значения, декодирование
  raw>>1 со знакорасширением масками 0xffffffc0 / 0xffffe000 /
  0xf0000000 (не инверсия знакового бита!).

Тесты: полный порт CompressedIntegerTests.cs (спековые байтовые
примеры II.23.2, граничные значения, invalid-префиксы) + поднабор
BlobReaderTests.cs + 6 round-trip тестов. Отклонение: C# sentinel
InvalidCompressedInteger → у нас исключения (IllegalArgumentException
для битых префиксов — аналог BadImageFormatException; ISE — out of
data); readBoolean добавлен в BlobReader (byte != 0).
C#-harness остаётся финальным оракулом в e2e (ADR 0011).
Итого в модуле 220 тестов, все зелёные.

## C. Внедрение в compiler-plugin

Отдельная задача после стабилизации модуля (все блоки I1–I6 + M1).
План и декомпозиция — [plan/COMPILER-INTEGRATION.md](plan/COMPILER-INTEGRATION.md),
решение — ADR 0010.

- [x] S1: вертикальный срез — `backend=pe` в CLI плагина, `PeIlEmitter`
      (парсинг текстовых member-ref'ов → метаданные, отложенное
      кодирование тел), `kotlinc-net.sh -pe` без ilasm;
      03-hello зелёный обоими путями (запуск + verifier);
- [x] S2: пользовательские функции и локальные переменные — 00-int-add:
      pe-DLL потреблена C#-consumer'ом, test_add(2,3)=5;
- [x] S3: ветвления/выражения — 02-expr: consumer печатает все строки;
- [x] S4: циклы break/continue — 04-loops (6 строк, точное совпадение)
      и 4 spec-теста печатают OK; попутно: порядок opcode/token в
      box/newarr, снятие CIL-кавычек с имён ('box'), копирование
      артефакта при -o в kotlinc-net.sh
- [x] S5: `just test all` гоняет оба бэкенда: build-test.sh после il-прогона
      собирает и проверяет pe-вариант (запуск с теми же ожиданиями +
      verifier на каждый pe-артефакт); для dll-тестов pe-DLL временно
      подменяет il-DLL по пути из HintPath consumer'а
- [x] S6: дефолт `backend=pe` (il — fallback через флаг);
      kotlinc-net.sh без флагов пишет PE, `-il` — старый путь;
      build-test.sh фиксирует backend=il для il-ветки явно;
      TextIlEmitter помечен @Deprecated (удаление — отдельный коммит,
      по плану); AGENTS.md обновлён

## C2. Долг качества кода reader'а (I8) — ревью после стабилизации

Отмечено при ревью I8 (не блокирует, но вычистить до выхода из PoC):

- [ ] **MetadataReader.kt**: паттерн `major = readUInt16(q); q += 2`
      заменить на потоковое чтение (`major = something.read()`) —
      ручное продвижение курсора рассыпается по всему файлу;
- [ ] **MetadataReader.kt**: много дублирующегося кода между
      таблицами (декодирование полей, widths, rowStart) — найти пути
      рефакторинга (генерация? единый row-decoder DSL?);
- [ ] **MetadataReader.kt / BlobReader.kt**: `companion object` с
      константами выглядит неидиоматично — пересмотреть (top-level
      const val / объекты с говорящими именами).

## D. Идеи (отложенные)

- [I-02](I-02-csharp-codegen-idea.md): генерация C# по Kotlin;
- [I-03](I-03-generic-handle-factory.md): generic-фабрика хэндлов;
- DebugDirectory (~700 строк): вернуть при необходимости;
- Stream sink interface: если появится потребитель больших сборок.
