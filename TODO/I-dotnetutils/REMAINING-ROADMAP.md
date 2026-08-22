# Оставшийся roadmap порта dotnetutils и внедрения

Этот файл — единый трекинг оставшейся работы после сессии 2026-08-22
(I0–I5 выполнены и закоммичены; см. IMPLEMENTATION-PLAN.md для
истории). Пункты идут в рекомендуемом порядке выполнения.

## A. Тесты: добивка непортированных фактов

| Файл | Фактов C# | Сейчас | Осталось | Приоритет |
|---|--:|--:|--:|---|
| BlobTests.cs | 41 | ~15 | ~26 | **высокий** |
| BlobUtilitiesTests.cs | 1 | ~3 | добивка | низкий |
| BlobContentIdTests.cs | 4 | ~2 | ~2 | низкий |
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

### B.3. I7: BlobEncoders (24 encoder-структуры, ~1390 строк)

Custom attribute / signature encoding. Может идти параллельно
с I5/I6 (нужны только I2+I4).

### B.4. I8: Минимальный MetadataReader (~3000–4000 строк)

Отдельное решение после M1. Заменит C#-harness для самопроверки
и откроет round-trip тесты (TagToTokenTests, MethodBodyBlock.Create).

## C. Внедрение в compiler-plugin

Отдельная задача после стабилизации модуля (все блоки I1–I6 + M1):

- [ ] Заменить IL-текстовый эмиттер (`TextIlEmitter`) на вызовы
      `InstructionEncoder` / `MethodBodyStreamEncoder` из dotnetutils;
- [ ] Заменить ilasm-шаг в `kotlinc-net.sh` на прямую запись PE
      через `MetadataRootBuilder` + `ManagedPEBuilder`;
- [ ] Перевести существующие e2e-тесты (`just test`) на новый путь;
- [ ] Зафиксировать ADR-0010: замена ilasm на прямой PE-эмиттер.

## D. Идеи (отложенные)

- [I-02](I-02-csharp-codegen-idea.md): генерация C# по Kotlin;
- [I-03](I-03-generic-handle-factory.md): generic-фабрика хэндлов;
- DebugDirectory (~700 строк): вернуть при необходимости;
- Stream sink interface: если появится потребитель больших сборок.
