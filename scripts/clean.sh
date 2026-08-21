#!/usr/bin/env bash
# scripts/clean.sh — очистка артефактов проекта.
#
# Использование:
#   clean.sh <category>
#     category ∈ all | build | sdk | sources  (default: all)
#
# Примеры:
#   clean.sh             # all (требует пере-bootstrap!)
#   clean.sh build       # только сборочные артефакты
#   clean.sh sdk         # только .sdk/
#   clean.sh sources     # только .sources/* (кроме README.md)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

ensure_env
PROJECT_ROOT="${PROJECT_ROOT:-$KOTLIN_DOTNET_PROJECT_ROOT}"
cd "$PROJECT_ROOT"

category="${1:-all}"

clean_build() {
    rm -rf build/ runtime/bin/ runtime/obj/ \
        kotlin-dotnet-engine/compiler-plugin/build/ kotlin-dotnet-engine/dotnetutils/build/
    echo ">>> Removed build artifacts only. SDK and sources preserved."
}

clean_sdk() {
    rm -rf .sdk/
    echo ">>> Removed .sdk/. Run 'just bootstrap' (or 'just sdks') to reinstall." >&2
}

clean_sources() {
    # Сохраняем .sources/README.md, удаляем остальное.
    if [ -d .sources ]; then
        find .sources -mindepth 1 ! -name README.md -exec rm -rf {} + 2>/dev/null || true
    fi
    echo ">>> Removed .sources/* (except README.md). Run 'just sources' to reinstall." >&2
}

case "$category" in
    all)
        clean_build
        clean_sdk
        clean_sources
        echo ">>> Removed build artifacts, SDK, and sources. Run 'just bootstrap' to reinstall." >&2
        ;;
    build)
        clean_build
        ;;
    sdk)
        clean_sdk
        ;;
    sources)
        clean_sources
        ;;
    *)
        log_error "unknown category: '$category' (expected: all | build | sdk | sources)"
        exit 1
        ;;
esac
