# CLI-опции плагина kotlin.dotnet

Опции передаются компилятору Kotlin через
`kotlinc -Xplugin=<jar> -P plugin:kotlin.dotnet:<option>=<value>`.
Обработка — `DotnetCommandLineProcessor` (pluginId: `kotlin.dotnet`);
неизвестная опция = ошибка компиляции (fail-fast).

| Опция | Значения | Дефолт | Назначение |
|---|---|---|---|
| `output.dir` | путь | `build/` | Директория артефактов плагина (ir-dump, PE-файл). См. ADR 0007 |
| `output.kind` | `exe` \| `dll` | `exe` | Тип сборки на выходе |
| `config` | `debug` \| `release` | `release` | Режим сборки: `debug` добавляет assembly-level `DebuggableAttribute(0x0107)` — JIT без оптимизаций, как csc `/debug+`; `release` — без атрибута |
| `stdlib.mode` | `lenient` \| `strict` | `lenient` | Строгость покрытия stdlib: `strict` — ошибка компиляции со сводным списком непокрытых `kotlin.*` символов; `lenient` — предупреждения. См. ADR 0014 |

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

- (пока нет; кандидат — portable PDB / sequence points)
