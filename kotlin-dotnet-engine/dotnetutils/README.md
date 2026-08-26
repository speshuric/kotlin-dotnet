# dotnetutils — порт write-path System.Reflection.Metadata на Kotlin

Пакет `org.kotlindotnet.dotnetutils.system.reflection` — Kotlin-порт
write-части `System.Reflection.Metadata` (SRM) из dotnet/runtime.
Обеспечивает полный цикл создания .NET-сборки без ilasm:
MetadataBuilder + кучи + таблицы + PE-запись (`PEBuilder` /
`ManagedPEBuilder`) → файл DLL/EXE. Архитектура и мотивация —
[ADR 0009](../../adr/0009-srm-port-to-kotlin.md); план и статус —
[TODO/I-dotnetutils](../../TODO/I-dotnetutils/REMAINING-ROADMAP.md).

## Upstream (база порта)

| Поле | Значение |
|---|---|
| Репозиторий | https://github.com/dotnet/runtime |
| Ветка | `release/10.0` |
| Коммит | `4a4758eb06bc1fa42fb69442af63f30026a23c9e` |
| Дата коммита | 2026-08-19 |
| Путь в апстриме | `src/libraries/System.Reflection.Metadata/src/` |

Все файлы порта несут шапку с указанием origin. При проверке обновлений
за апстримом: диффать `src/` апстрима от зафиксированного коммита;
если write-path изменился — переносим дельту в наш порт.

## Структура (зеркало неймспейсов оригинала)

```
system.reflection.metadata/            Metadata, Handles.TypeSystem, Blobs
├── ecma335/                           MetadataBuilder, MetadataRootBuilder,
│   ├── encoding/…                     энкодеры сигнатур и атрибутов
│   └── …                              InstructionEncoder, MethodBodyStream…
└── portableexecutable/                PEHeader(Builder), PEBuilder,
                                       ManagedPEBuilder, секции
```

## Ограничения

- Только write-path; reader (`PEReader`/`MetadataReader`) не портирован.
- Отсечено: Edit-and-Continue, WinMD, пулинг билдеров, DebugDirectory.
- Portable PDB: частично (K-01 L2) — debug-таблицы Document /
  MethodDebugInformation / LocalScope / LocalVariable и standalone-корень
  через `PdbBuilder`; ImportScope/LocalConstant/StateMachineMethod не
  портированы.
- Чистый Kotlin stdlib: прямые `import java.*` запрещены.

## Тесты

Юнит-тесты — golden-bytes порты тестов dotnet/runtime
(`tests/Metadata/Ecma335`, `tests/PortableExecutable`) +
адаптированные факты; для debug-таблиц — кейсы в
`MetadataBuilderAddTests.kt` по апстримным `Add*` и smoke
`PdbBuilderSmokeTest.kt` (самосогласованность standalone-образа). E2E — тест `05-pe-hello`
(`just test 05-pe-hello`): hello-world EXE собирается этим модулем,
проверяется запуском через `dotnet` и C#-harness'ом
`kotlin-dotnet-utils/verifier`.
