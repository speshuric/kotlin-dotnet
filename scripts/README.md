# scripts/

Общие скрипты проекта `kotlin-dotnet` для настройки окружения,
установки SDK/исходников и компиляции `.kt` → `.NET`.

## Структура

| Файл              | Назначение                                              |
|-------------------|---------------------------------------------------------|
| `common.sh`        | Общие функции (source-only, **не исполняется**).        |
| `activate.sh`     | Активация локального окружения (JAVA_HOME, DOTNET_ROOT). |
| `deactivate.sh`   | Снятие env, установленного `activate.sh`.              |
| `install-sdks.sh` | Установка JDK/kotlinc/.NET/Gradle в `.sdk/`.            |
| `install-sources.sh` | Shallow-clone исходников в `.sources/`.              |
| `kotlinc-net.sh`  | CLI: `.kt` → `.exe`/`.dll`.                             |

## common.sh

**Source-only** — не исполняется напрямую, подключается через
`source "$SCRIPT_DIR/common.sh"`. Вызывающий скрипт сам выставляет
`set -euo pipefail` (см. принцип в `TODO/README.md`).

### Что предоставляет

- `PROJECT_ROOT` — абсолютный путь к корню проекта
  (вычисляется через `BASH_SOURCE` самого `common.sh`).
- `log_info <msg>` — `echo "[kotlin-dotnet] <msg>"` в stdout.
- `log_warn <msg>` — `echo "[kotlin-dotnet] WARN: <msg>"` в stderr.
- `log_error <msg>` — `echo "[kotlin-dotnet] ERROR: <msg>"` в stderr.
- `ensure_env` — если `KOTLIN_DOTNET_PROJECT_ROOT` не задан,
  `source`-ит `scripts/activate.sh`.
- `require_file <path> [msg]` — если файла нет, печатает
  `log_error` и завершает скрипт с `exit 1`.

### Пример использования

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

ensure_env
require_file "$PROJECT_ROOT/.sdk/jdk/bin/java" "JDK не установлен: run scripts/install-sdks.sh"
log_info "готово"
```

## Заметки

- `activate.sh` намеренно **не** зависит от `common.sh`
  (chicken-egg: `common.sh::ensure_env` вызывает `activate.sh`).
- `deactivate.sh` симметричен `activate.sh` и тоже не зависит
  от `common.sh`.
- `justfile` использует `source scripts/activate.sh` в рецептах
  напрямую (явно и просто); миграция на `common.sh` не обязательна.
