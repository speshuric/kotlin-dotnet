# ADR 0009: Порт write-path System.Reflection.Metadata на Kotlin (модуль dotnetutils)

- **Дата:** 2026-08-21
- **Статус:** Accepted
- **Связанные:** ADR 0001 (pipeline IR→IL→ilasm — заменяется по частям), задача [I-01](../TODO/I-dotnetutils/I-01-metadata-builder.md)

## Контекст

Текущий pipeline генерации сборки: IR → CIL-текст (`.il`) → внешний
процесс `ilasm` → PE DLL/EXE (ADR 0001). Ограничения решения:

1. **Зависимость от текстового представления ilasm** — синтаксис слабо
   специфицирован вне ECMA-335, хрупок (экранирование имён, кавычки,
   кодировки). Мы уже обжигались (`NameMapper` экранирует имена,
   совпадающие с CIL-опкодами).
2. **Производительность**: цепочка stop-and-go — тяжеловесный JVM-плагин
   пишет текстовый файл, затем отдельный процесс ilasm парсит его заново.
3. **Отсутствие контроля формата**: ошибки ilasm диагностируются по его
   текстовым сообщениям, а не в момент генерации.

При этом:

- Полная спецификация формата доступна: ECMA-335
  (`docs/ECMA-335_6th_edition_june_2012.pdf`).
- Эталонная реализация записи метаданных доступна с исходниками:
  `System.Reflection.Metadata` (SRM) в dotnet/runtime
  (`.sources/dotnet-runtime/src/libraries/System.Reflection.Metadata/`),
  лицензия MIT (.NET Foundation).
- Kotlin и C# достаточно близки, чтобы перенос был механическим.

## Решение

Полностью транслировать write-path SRM из C# в Kotlin как независимый
Gradle-модуль `kotlin-dotnet-engine/dotnetutils`
(пакет `org.kotlindotnet.dotnetutils.system.reflection.metadata`,
отражающий неймспейсы оригинала). Модуль обеспечивает полный цикл
создания .NET-сборок: MetadataBuilder + кучи + таблицы + PE-запись
(`PEBuilder`/`ManagedPEBuilder`) → файл DLL/EXE. Цель — замена шага
«IL-текст + ilasm» на прямую генерацию PE.

### Принятые принципы переноса

1. **Чистый Kotlin stdlib.** Прямые `import java.*` в модуле запрещены
   (закрепить проверкой). kotlin-stdlib на JVM делегирует в java.*, но
   на уровне исходников модуль остаётся «чисто котлиновым» — при будущем
   самосборном таргете kotlin.* маппится в BCL без выковыривания.
2. **Имена**: пакет/классы повторяют структуру оригинала
   (`system.reflection.metadata`, `MetadataBuilder`, `BlobBuilder`…),
   соглашения именования Kotlin: классы PascalCase, методы/свойства
   lowerCamelCase, константы и enum-entries UPPER_SNAKE.
3. **Доменные типы** (`EntityHandle`, `Blob`, токены, хэндлы таблиц) —
   перенос 1:1. Публичные методы по умолчанию переносятся все.
4. **BCL-типы в сигнатурах** заменяются котлиновскими аналогами:
   `ImmutableArray<T>` → `List<T>`, `Stream` → свой минимальный вывод /
   запись файла, `Span<T>`/`MemoryMarshal` → `ByteArray` + ручная
   индексация (в write-path их немного).
5. **Отсечение заведомо мёртвого для PoC** (~2–3 тыс. строк):
   Edit-and-Continue (`#JTD`, EnC-delta), WinMD, Portable PDB /
   embedded PDB (`PortablePdbBuilder`).
6. **Без пулинга**: `ArrayPool`/`PooledBlobBuilder` не переносятся —
   обычные буферы; многопоточности в write-path нет.

### Объём (замеры по release/10.0)

| Блок | Строк C# |
|---|---|
| Ecma335: MetadataBuilder (+Heaps/+Tables), MetadataRootBuilder, MetadataSizes, SerializedMetadataHeaps | ~3500 |
| Blob*/Internal (write-часть; BlobReader и пр. read-only не входят) | ~3000–5000 |
| PortableExecutable write-path (PEBuilder, ManagedPEBuilder, ManagedTextSection, заголовки) | ~1500–2000 |
| IL: ILOpCode enum + ILOpCodeExtensions (таблица опкодов уже в SRM) | ~500 |
| **Итого write-path** | **~12–15 тыс.** |

### Верификация

Родные тесты SRM частично верифицируются через `MetadataReader`
(round-trip). Стратегия поэтапная:

1. **Итерации 1–N (write-path):**
   - юнит-тесты через внутреннее состояние (счётчики строк таблиц,
     размеры куч — как родные `MetadataBuilderTests`);
   - **C#-harness** на настоящем SRM (dotnet SDK уже в проекте):
     открывает наш DLL, ассертит содержимое — ground truth без порта;
   - e2e через существующие тесты (`just test`) — запуск реальных EXE.
2. **Отдельная итерация после первого рабочего EXE:** минимальный
   MetadataReader на Kotlin (~3–4 тыс. строк: ядро 1499 + MetadataTokens
   526 + структуры строк таблиц + чтение куч) — заменяет C#-harness в
   регрессионных тестах, пригоден для отладки. Если окажется тяжелее
   оценки — остаёмся на варианте с harness.
3. Тесты `PEBuilderTests` адаптируются (8 фактов, часть про PEReader —
   пропускается).

### Лицензия

SRM — MIT (.NET Foundation). В шапках файлов порта указывается origin;
условия лицензии — MIT, унаследованные от апстрима.

### Зафиксированная база порта (upstream pin)

Порт выполнялся от следующего состояния апстрима:

- репозиторий: https://github.com/dotnet/runtime
- ветка: `release/10.0`
- коммит: `4a4758eb06bc1fa42fb69442af63f30026a23c9e` (2026-08-19)
- путь: `src/libraries/System.Reflection.Metadata/src/`

Дублируется в README модуля (`kotlin-dotnet-engine/dotnetutils/README.md`)
и в `TODO/I-dotnetutils/REMAINING-ROADMAP.md`. При проверке обновлений:
диффать апстрим от этого коммита и переносить дельту write-path в порт.

## Последствия

+ Убирается зависимость от текстового формата ilasm и внешнего процесса.
+ Ошибки формата ловятся в момент генерации, а не при сборке.
+ Таблица опкодов берётся из SRM (ILOpCode), а не транскрибируется из PDF.
− Разовая стоимость порта ~12–15 тыс. строк + сопровождение диффа
  против апстрима (осознанно; апстрим write-path стабилен).
− До завершения порта поддерживаются оба пути генерации (ilasm остаётся
  рабочим до переключения compiler-plugin).

## TODO

- Переключение `compiler-plugin` на новый путь генерации — отдельная
  задача после стабилизации модуля (см. TODO I-dotnetutils).
- Закрепить запрет `import java.*` проверкой в Gradle-сборке модуля.
- Идея «на подумать» (см. [I-02](../TODO/I-dotnetutils/I-02-csharp-codegen-idea.md)):
  генерация C#-кода по Kotlin для механизации проработки кейсов.

## См. также

- Паритет структур апстрима (какие C#-struct во что портированы и
  исключения): [`docs/dotnetutils-struct-parity.md`](../docs/dotnetutils-struct-parity.md).
