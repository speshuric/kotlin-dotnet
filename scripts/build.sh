#!/usr/bin/env bash
# scripts/build.sh — собрать компоненты проекта.
#
# Использование:
#   build.sh <target> <config> [--no-test]
#     target  ∈ plugin | runtime | all
#     config  ∈ debug | release
#
# Примеры:
#   build.sh plugin debug           # только plugin JAR
#   build.sh runtime release        # только KotlinDotnetRuntime.dll
#   build.sh all debug               # plugin + runtime + все тесты
#   build.sh all debug --no-test     # без верификации тестов
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

# --- Разбор аргументов ---
target="${1:-}"
config="${2:-debug}"
no_test_arg=""
shift 2 2>/dev/null || shift $# 2>/dev/null || true
while [ $# -gt 0 ]; do
    case "$1" in
        --no-test) no_test_arg="--no-test"; shift ;;
        *) log_error "unexpected argument: $1"; exit 1 ;;
    esac
done

if [ -z "$target" ]; then
    log_error "usage: $0 <target: plugin|runtime|all> <config: debug|release> [--no-test]"
    exit 1
fi

case "$config" in
    debug|release) ;;
    *) log_error "config must be 'debug' or 'release' (got: $config)"; exit 1 ;;
esac

build_plugin() {
    local engine="$PROJECT_ROOT/kotlin-dotnet-engine"
    log_info "build: plugin (gradlew :compiler-plugin:jar)"
    (cd "$engine" && ./gradlew :compiler-plugin:jar)
    local jar="kotlin-dotnet-engine/compiler-plugin/build/libs/dotnet-compiler-plugin-0.1.0-SNAPSHOT.jar"
    require_file "$jar" "plugin JAR not found: $jar"
    log_info ">>> plugin OK ($jar)"
}

build_runtime() {
    local cfg
    case "$1" in
        release) cfg="Release" ;;
        debug)   cfg="Debug" ;;
    esac
    log_info "build: runtime (dotnet build runtime/ -c $cfg)"
    dotnet build runtime/ -c "$cfg"
    local dll="runtime/bin/$cfg/net10.0/KotlinDotnetRuntime.dll"
    require_file "$dll" "runtime DLL not found: $dll"
    log_info ">>> runtime OK ($dll)"
}

case "$target" in
    plugin)
        build_plugin
        ;;
    runtime)
        build_runtime "$config"
        ;;
    all)
        # shellcheck disable=SC1091
        source "$PROJECT_ROOT/scripts/tests.sh"
        build_plugin
        build_runtime "$config"
        local_cfg_flag="--$config"
        id=""
        for id in $TEST_IDS; do
            "$PROJECT_ROOT/scripts/build-test.sh" "$id" "$local_cfg_flag" $no_test_arg
        done
        log_info ">>> all build targets OK"
        ;;
    *)
        log_error "unknown target: '$target' (expected: plugin | runtime | all)"
        exit 1
        ;;
esac
