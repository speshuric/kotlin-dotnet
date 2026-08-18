# A-06: scripts/ review — вынести общие части

- **Тема:** A. Infrastructure
- **Разметка:** PRE-9
- **Зависимости:** A-01 (Log — для общей функции логирования скриптов)
- **Статус:** TODO

## Контекст

Папка: `scripts/`.

Файлы:
- `activate.sh` — активация env (JAVA_HOME, DOTNET_ROOT, PATH).
- `deactivate.sh` — снять env.
- `install-sdks.sh` — установка JDK/kotlinc/.NET/Gradle в `.sdk/`.
- `install-sources.sh` — shallow clones в `.sources/`.
- `kotlinc-net.sh` — CLI: `.kt` → `.exe`/`.dll`.

Дублирование:
1. **Определение `PROJECT_ROOT`/`SCRIPT_DIR`** — в каждом скрипте:
   ```bash
   SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
   PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
   ```
   (в `install-sdks.sh`, `install-sources.sh`, `kotlinc-net.sh`).
   `activate.sh` имеет более сложную логику (bash/zsh/sh).
2. **Префикс логов** — `[kotlin-dotnet]` в `activate.sh:53`,
   `[kotlinc-net]` в `kotlinc-net.sh`. Нет общей функции `log_info`/
   `log_warn`/`log_error`.
3. **Активация env** — `kotlinc-net.sh` дёргает `source activate.sh`
   если `KOTLIN_DOTNET_PROJECT_ROOT` не задан. `justfile` — в каждом
   рецепте `source scripts/activate.sh`. Нет функции «убедись что env
   активирован, иначе активируй».
4. **Проверка существования артефактов** — в `kotlinc-net.sh` и
   `justfile` повторяются проверки `if [ ! -f "$PLUGIN_JAR" ]` и т.д.

## Цель

Вынести общие части в `scripts/common.sh` (sourced, не исполняемый),
чтобы остальные скрипты делали `source "$SCRIPT_DIR/common.sh"` и
получали:
- `project_root` — переменная.
- `log_info`/`log_warn`/`log_error` — функции с общим префиксом.
- `ensure_env_activated` — функция, активирующая env, если ещё не.
- `require_file` — функция-assert, что файл существует (с понятной
  ошибкой).

## Задачи

1. Создать `scripts/common.sh`:
   ```bash
   # scripts/common.sh — общие функции для scripts/.
   # Source-ится, не исполняется.
   PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
   log_info()  { echo "[kotlin-dotnet] $*"; }
   log_warn()  { echo "[kotlin-dotnet] WARN: $*" >&2; }
   log_error() { echo "[kotlin-dotnet] ERROR: $*" >&2; }
   ensure_env() {
       if [ -z "${KOTLIN_DOTNET_PROJECT_ROOT:-}" ]; then
           source "$PROJECT_ROOT/scripts/activate.sh"
       fi
   }
   require_file() {
       local f="$1"
       local msg="${2:-file not found: $f}"
       if [ ! -f "$f" ]; then log_error "$msg"; exit 1; fi
   }
   ```
2. Мигрировать `install-sdks.sh`, `install-sources.sh`, `kotlinc-net.sh`:
   - Убрать локальное определение `SCRIPT_DIR`/`PROJECT_ROOT`.
   - `source "$SCRIPT_DIR/common.sh"` в начале.
   - Заменить `echo ">>> ..."` на `log_info` (или оставить `>>>` для
     install-скриптов, если так удобнее — на усмотрение, но единообразно).
   - Заменить `echo "error: ..."` на `log_error`.
3. Мигрировать `kotlinc-net.sh`:
   - Убрать блок «Активируем, если ещё не активировано» (строки 46-50),
     заменить на `ensure_env`.
   - Заменить проверки `if [ ! -f "$PLUGIN_JAR" ]` на `require_file`.
4. `activate.sh` — оставить как есть (он сам по себе логика активации),
   но вынести `project_root` в `common.sh` и source его. Осторожно:
   `activate.sh` вызывается до того, как `common.sh` может быть
   source-нут (chicken-egg). Решение: `activate.sh` не зависит от
   `common.sh`, а `common.sh` может дёргать `activate.sh` через
   `ensure_env`.
5. `justfile` — можно оставить `source scripts/activate.sh` в рецептах
   (простой и явный), либо сделать единый `_activate`-рецепт-зависимость.
   Не критично; общий `source scripts/common.sh && ensure_env` тоже
   работает. **Рекомендация:** оставить `justfile` как есть, только
   `kotlinc-net.sh` и install-скрипты мигрируют на `common.sh`.
6. Документировать в `scripts/README.md` (создать), что `common.sh` —
   source-only, и какие функции доступны.

## Приёмка

- `grep -rn 'SCRIPT_DIR=' scripts/` → только в `common.sh` и `activate.sh`.
- `grep -rn 'echo "\[kotlin-dotnet\]' scripts/` → только в `common.sh`.
- `just bootstrap && just test-all` зелёный.
- Поведение скриптов (вывод, коды возврата) не изменилось.

## Заметки исполнителю

- `common.sh` — source-only, `set -euo pipefail` выставляет вызывающий
  скрипт, не `common.sh`.
- Не переусложнять: 4 функции, 1 переменная. Это не фреймворк.
- `deactivate.sh` — трогать не обязательно (он симметричен `activate.sh`,
  вынос в common не упростит).
