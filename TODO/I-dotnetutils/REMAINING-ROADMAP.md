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
  уже покрыты косвенно через I2BlobCoreTests, но нужен прямой порт.
- Все тесты верифицируются без MetadataReader (только writer side).

## B. Итерации порта (из IMPLEMENTATION-PLAN.md)

### B.1. I6: PE write-path (~2360 строк C#) — следующий блок

Файлы: `PEHeader(+Builder)`, `CoffHeader`, `CorHeader`,
`PEDirectoriesBuilder`, `SectionHeader`, `ManagedTextSection`,
`PEBuilder`(+Section), `ManagedPEBuilder`, `ResourceSectionBuilder`.

Тесты: `PEHeaderBuilderTests`, `PEBuilderTests`.

Блокирует M1.

### B.2. M1: Майлстон — первый EXE целиком Kotlin'ом

Критерии приёмки (см. IMPLEMENTATION-PLAN.md):
1. C#-harness открывает сборку настоящим SRM;
2. `dotnet` запускает EXE;
3. `dotnet-ildasm` даёт корректный IL;
4. Экспериментальный recipe рядом с ilasm-путём.

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
