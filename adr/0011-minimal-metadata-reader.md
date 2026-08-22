# ADR 0011: Минимальный MetadataReader в dotnetutils

- **Дата:** 2026-08-22
- **Статус:** Accepted
- **Связанные:** ADR 0009 (порт write-path; этот ADR расширяет скоп
  модуля на read-path), REMAINING-ROADMAP §I8

## Контекст

Модуль dotnetutils — write-path only (ADR 0009). Самопроверка артефактов
выполняется C#-harness'ом (`kotlin-dotnet-utils/verifier`, настоящий SRM).
Проблемы:

1. Нет round-trip тестов: writer → reader → сравнение строк таблиц/куч.
   Fidelity-регрессии ловятся только рантаймом .NET (пример: порядок
   opcode/token в box/newarr на интеграции S2–S4).
2. e2e требует запуска `dotnet` на каждый pe-артефакт.

## Решение

Портировать **минимальный** read-path:

- `BlobReader` (compressed integers, UTF8/UTF16, serialized strings);
- представления куч #Strings/#US/#Blob/#Guid над ByteArray;
- парс metadata root + stream headers (#~, #Strings, #US, #Guid, #Blob);
- строки таблиц, которые пишет наш компилятор: Module, TypeRef, TypeDef,
  Field, MethodDef, Param, MemberRef, StandAloneSig, TypeSpec, Assembly,
  AssemblyRef (11 из ~45);
- read-side конструкторы уже существующих PE-структур +
  rva→file-offset по таблице секций;
- `MethodBody` (tiny/fat header, IL-slice, exception regions).

Размещение: пакет `...system.reflection.metadata.reader`
(MetadataReader, PEReader-lite), `...metadata.BlobReader`.

### Не портируется (non-goals)

Остальные ~34 таблицы; WinMD/EnC; Portable PDB; провайдеры Decoding/
(`SignatureDecoder`, `CustomAttributeDecoder`); `TypeName*`;
стримовые опции `MetadataReaderProvider` (только ByteArray);
кеширующие оптимизации строк.

## Оракул

Наш reader делит конвенции с нашим writer — он НЕ независимый оракул.
C#-harness (`kotlin-dotnet-utils/verifier`) остаётся финальной проверкой
в e2e; I8 используется для плотных round-trip тестов в Gradle.

## Последствия

+ Round-trip сетка против fidelity-регрессий.
+ Быстрая JVM-only диагностика (без `dotnet run` на каждый чих).
− Двойная поверхность диффа против апстрима (осознанно; reader стабилен).
− Риск shared-blindspot между writer/reader — закрывается сохранением
  SRM-harness.
