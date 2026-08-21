#!/usr/bin/env bash
# scripts/test.sh — запустить тесты по селектору.
#
# Использование:
#   test.sh <selector>
#     selector ∈ all | <testid> | <glob> (например 04*)
#
# Примеры:
#   test.sh              # all
#   test.sh all
#   test.sh 00-int-add
#   test.sh 04*
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

ensure_env
PROJECT_ROOT="${PROJECT_ROOT:-$KOTLIN_DOTNET_PROJECT_ROOT}"
cd "$PROJECT_ROOT"

# --- DSH prelude: writable HOME/GRADLE/XDG (read-only корневая ФС) ---
export GRADLE_USER_HOME="$PROJECT_ROOT/build/tmp/gradle-home"
export XDG_RUNTIME_DIR="$PROJECT_ROOT/build/tmp/runtime"
export HOME="$PROJECT_ROOT/build/tmp/home"
export DOTNET_CLI_HOME="$HOME"
mkdir -p "$GRADLE_USER_HOME" "$XDG_RUNTIME_DIR" "$HOME"

# shellcheck disable=SC1091
source "$PROJECT_ROOT/scripts/tests.sh"

selector="${1:-all}"
ids="$(resolve_selector "$selector")" || exit 1

if [ -z "$ids" ]; then
    log_error "no tests resolved for selector '$selector'"
    exit 1
fi

# --- Гарантируем, что plugin + runtime собраны (пропускаем если уже есть) ---
PLUGIN_JAR="kotlin-dotnet-engine/compiler-plugin/build/libs/dotnet-compiler-plugin-0.1.0-SNAPSHOT.jar"
if [ ! -f "$PLUGIN_JAR" ]; then
    log_info "plugin JAR missing, building..."
    (cd "$PROJECT_ROOT/kotlin-dotnet-engine" && ./gradlew :compiler-plugin:jar -q)
fi
require_file "$PLUGIN_JAR" "plugin JAR not found: $PLUGIN_JAR"

RUNTIME_DLL=""
for cand in \
    runtime/bin/Release/net10.0/KotlinDotnetRuntime.dll \
    runtime/bin/Debug/net10.0/KotlinDotnetRuntime.dll; do
    if [ -f "$cand" ]; then RUNTIME_DLL="$cand"; break; fi
done
if [ -z "$RUNTIME_DLL" ]; then
    log_info "runtime DLL missing, building..."
    dotnet build runtime/ -c Release
    RUNTIME_DLL="runtime/bin/Release/net10.0/KotlinDotnetRuntime.dll"
fi
require_file "$RUNTIME_DLL" "runtime DLL not found: $RUNTIME_DLL"

# --- Запуск каждого теста ---
failed=0
passed=0
for id in $ids; do
    if "$PROJECT_ROOT/scripts/build-test.sh" "$id" --release; then
        passed=$((passed + 1))
    else
        failed=$((failed + 1))
    fi
done

echo ""
echo ">>> tests: $passed passed, $failed failed (selector='$selector')"
if [ "$failed" -gt 0 ]; then
    exit 1
fi
echo ">>> all tests OK"
