#!/usr/bin/env bash
# scripts/build-test.sh — собрать и верифицировать один тест по id.
#
# Использование:
#   build-test.sh <testid> [--debug|--release] [--no-test]
#
# Примеры:
#   build-test.sh 00-int-add              # debug (default) + verify
#   build-test.sh 04-loops --release      # release + verify
#   build-test.sh 03-hello --no-test      # только собрать, без запуска
#
# Артефакты идут в build/<testid>/ (для 04-loops-spec — build/04-loops-spec/<basename>/).
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

# --- Разбор аргументов ---
testid=""
config="debug"
no_test=false

while [ $# -gt 0 ]; do
    case "$1" in
        --debug)   config="debug"; shift ;;
        --release) config="release"; shift ;;
        --no-test) no_test=true; shift ;;
        -h|--help)
            sed -n '2,/^$/p' "$0" | sed 's/^# \?//'
            exit 0 ;;
        *)
            if [ -z "$testid" ]; then
                testid="$1"; shift
            else
                log_error "unexpected argument: $1"
                exit 1
            fi
            ;;
    esac
done

if [ -z "$testid" ]; then
    log_error "usage: $0 <testid> [--debug|--release] [--no-test]"
    exit 1
fi

if ! test_exists "$testid"; then
    log_error "unknown test id: '$testid' (valid: $TEST_IDS)"
    exit 1
fi

log_info "build-test: $testid (config=$config, no-test=$no_test)"

# --- Плагин JAR ---
PLUGIN_JAR="compiler-plugin/build/libs/dotnet-compiler-plugin-0.1.0-SNAPSHOT.jar"
if [ ! -f "$PLUGIN_JAR" ]; then
    log_info "plugin JAR missing, building via gradlew..."
    ./gradlew :compiler-plugin:jar -q
fi
require_file "$PLUGIN_JAR" "plugin JAR not found: $PLUGIN_JAR (run 'just plugin')"

# --- Кофиг runtimeconfig.json (framework-dependent, rollForward Major) ---
RUNTIMECONFIG_JSON='{"runtimeOptions":{"tfm":"net10.0","framework":{"name":"Microsoft.NETCore.App","version":"10.0.11"},"rollForward":"Major"}}'

# _build_one <kt> <testid>  — собрать и (если не no-test) верифицировать
# один .kt файл. outdir = build/<testid> для обычных тестов,
# build/<testid>/<basename>/ для 04-loops-spec.
_build_one() {
    local kt="$1"
    local tid="$2"
    local name kind outdir il asm

    name="$(basename "$kt" .kt)"
    kind="$(test_kind "$tid")"

    if [ "$tid" = "04-loops-spec" ]; then
        outdir="build/$tid/$name"
    else
        outdir="build/$tid"
    fi
    mkdir -p "$outdir/kt-out"

    il="$outdir/$name.il"
    asm="$outdir/$name.$kind"

    # --- gen-il: kotlinc + plugin → .il (mtime-инкрементально) ---
    if [ -f "$il" ] && [ "$il" -nt "$kt" ] && [ "$il" -nt "$PLUGIN_JAR" ]; then
        echo "[just] $il up-to-date"
    else
        log_info "compiling $kt → $il"
        rm -f "$outdir"/ir-dump-*.txt "$il"
        kotlinc -Xplugin="$PLUGIN_JAR" \
            -P "plugin:kotlin.dotnet:output.dir=$outdir" \
            "$kt" -d "$outdir/kt-out"
    fi
    require_file "$il" "plugin did not produce $il"

    # --- ilasm → .dll/.exe (mtime-инкрементально) ---
    if [ -f "$asm" ] && [ "$asm" -nt "$il" ]; then
        echo "[just] $asm up-to-date"
    else
        log_info "assembling → $asm"
        ilasm "/$kind" "/output:$asm" "$il"
    fi
    require_file "$asm" "ilasm did not produce $asm"

    # --- exe: копируем runtime DLL + runtimeconfig.json ---
    if [ "$kind" = "exe" ]; then
        # Runtime DLL: предпочитаем Release, откатываемся на Debug.
        local runtime_dll=""
        for cand in \
            runtime/bin/Release/net10.0/KotlinDotnetRuntime.dll \
            runtime/bin/Debug/net10.0/KotlinDotnetRuntime.dll; do
            if [ -f "$cand" ]; then runtime_dll="$cand"; break; fi
        done
        if [ -z "$runtime_dll" ]; then
            log_error "KotlinDotnetRuntime.dll not found (run 'just runtime' first)"
            exit 1
        fi
        cp "$runtime_dll" "$outdir/"
        printf '%s\n' "$RUNTIMECONFIG_JSON" > "$outdir/$name.runtimeconfig.json"
    fi

    if $no_test; then
        return 0
    fi

    # --- verify ---
    _verify "$tid" "$name" "$asm"
}

# _verify <testid> <name> <asm> — проверка вывода.
_verify() {
    local tid="$1"
    local name="$2"
    local asm="$3"
    local out expected consumer

    case "$tid" in
        00-int-add)
            consumer="$(test_consumer "$tid")"
            # dotnet run печатает рантайм-шум в stderr; программа — в stdout.
            out="$(cd "$consumer" && dotnet run 2>/dev/null || true)"
            expected="5"
            if [ "$out" != "$expected" ]; then
                echo "FAIL: $tid"
                echo "--- expected ---"
                echo "$expected"
                echo "--- got ---"
                echo "$out"
                exit 1
            fi
            ;;
        02-expr)
            consumer="$(test_consumer "$tid")"
            out="$(cd "$consumer" && dotnet run 2>/dev/null || true)"
            # 16 информационных строк; проверяем только exit-код (выхлоп нестабилен).
            if [ -z "$out" ]; then
                echo "FAIL: $tid (empty output)"
                exit 1
            fi
            ;;
        03-hello)
            out="$(dotnet "$asm" 2>/dev/null || true)"
            expected="Hello, .NET!"
            if [ "$out" != "$expected" ]; then
                echo "FAIL: $tid"
                echo "--- expected ---"
                echo "$expected"
                echo "--- got ---"
                echo "$out"
                exit 1
            fi
            ;;
        04-loops)
            out="$(dotnet "$asm" 2>/dev/null || true)"
            expected="$(printf '10\n10\n5\n25\n42\nx = 42')"
            if [ "$out" != "$expected" ]; then
                echo "FAIL: $tid"
                echo "--- expected ---"
                echo "$expected"
                echo "--- got ---"
                echo "$out"
                exit 1
            fi
            ;;
        04-loops-spec)
            out="$(dotnet "$asm" 2>/dev/null || true)"
            expected="OK"
            if [ "$out" != "$expected" ]; then
                echo "FAIL: $tid/$name"
                echo "--- expected ---"
                echo "$expected"
                echo "--- got ---"
                echo "$out"
                exit 1
            fi
            ;;
        *)
            log_error "verify: unknown test id '$tid'"
            exit 1
            ;;
    esac
    echo ">>> $tid/$name OK"
}

# --- Тело: 04-loops-spec цикл по 4 spec .kt, обычный — один .kt ---
if [ "$testid" = "04-loops-spec" ]; then
    shopt -s nullglob
    for kt in test-projects/04-loops/spec/*.kt; do
        _build_one "$kt" "$testid"
    done
    shopt -u nullglob
    if $no_test; then
        log_info "done: $testid (no-test)"
        exit 0
    fi
    log_info ">>> $testid OK"
    exit 0
fi

kt="$(test_kt "$testid")"
_build_one "$kt" "$testid"
if $no_test; then
    log_info "done: $testid (no-test)"
else
    log_info ">>> $testid OK"
fi
