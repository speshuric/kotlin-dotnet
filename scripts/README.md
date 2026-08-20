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
| `kotlinc-net.sh`  | CLI: `.kt` → `.exe`/`.dll` (per-test layout `build/<name>/`). |
| `tests.sh`        | Реестр тестов (source-only, id → .kt/kind/consumer).    |
| `build-test.sh`   | Сборка + верификация одного теста (`--debug`/`--release`/`--no-test`). |
| `build.sh`        | Диспетчер сборки: `plugin` \| `runtime` \| `all` + config. |
| `test.sh`         | Диспетчер тестов: selector → список id → `build-test.sh`. |
| `show.sh`         | `il` \| `ir` \| `disasm` × `last` \| `all` \| `<testid>` \| `<glob>`. |
| `clean.sh`        | Очистка: `all` \| `build` \| `sdk` \| `sources`.       |

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
- `justfile` использует `source scripts/activate.sh` в рецепте `bootstrap`
  (для verify-вывода). Сборочные рецепты делегируют в `scripts/build.sh`/
  `test.sh`/`show.sh`/`clean.sh`, которые сами вызывают `ensure_env`
  (через `common.sh`) + DSH-prelude (GRADLE_USER_HOME/XDG/HOME).
- `scripts/tests.sh` — source-only реестр тестов. При добавлении теста
  регистрировать его здесь (id → .kt, kind, consumer, описание).
- `scripts/build-test.sh` — mtime-инкрементальность (как `_gen-il`/`_gen-asm`
  в старом `justfile`), per-test layout (`build/<testid>/`).
