# scripts/common.sh — shared helpers for scripts/.
# Sourced, not executed.
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
log_info()  { echo "[kotlin-dotnet] $*"; }
log_warn()  { echo "[kotlin-dotnet] WARN: $*" >&2; }
log_error() { echo "[kotlin-dotnet] ERROR: $*" >&2; }
ensure_env() {
    if [ -z "${KOTLIN_DOTNET_PROJECT_ROOT:-}" ]; then
        # shellcheck disable=SC1091
        source "$PROJECT_ROOT/scripts/activate.sh"
    fi
}
require_file() {
    local f="$1"
    local msg="${2:-file not found: $f}"
    if [ ! -f "$f" ]; then log_error "$msg"; exit 1; fi
}
