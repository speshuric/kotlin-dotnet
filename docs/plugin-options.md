# CLI-опции плагина kotlin.dotnet

Опции передаются компилятору Kotlin через
`kotlinc -Xplugin=<jar> -P plugin:kotlin.dotnet:<option>=<value>`.
Обработка — `DotnetCommandLineProcessor` (pluginId: `kotlin.dotnet`);
неизвестная опция = ошибка компиляции (fail-fast).

| Опция | Значения | Дефолт | Назначение |
|---|---|---|---|
| `output.dir` | путь | `build/` | Директория артефактов плагина (ir-dump, PE-файл). См. ADR 0007 |
| `output.kind` | `exe` \| `dll` | `exe` | Тип сборки на выходе |

Примечания:

- Ключи конфигурации хранятся в companion object процессора
  (`CompilerConfigurationKey` с identity-`equals`) — см. kdoc
  `DotnetCommandLineProcessor`.
- IR-dump (`ir-dump-<module>.txt`) пишется всегда, best-effort.

## История

- **Удалено (ADR 0012):** `backend=il|pe` — IL-текстовый путь удалён;
  PE — единственный формат вывода. Опция снята с регистрации:
  `-P plugin:kotlin.dotnet:backend=il` теперь падает с ошибкой.

## Планируемые опции

- `config=debug|release` — уровень 1 задачи
  [K-01](../TODO/K-debug-builds/K-01-debug-release-config.md):
  DebuggableAttribute + JIT без оптимизаций.
