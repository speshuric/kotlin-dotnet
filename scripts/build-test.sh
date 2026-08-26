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
# Тест = папка в test-projects/ с test.properties (формат: docs/test-format.md).
# Атрибуты (kind/backends/sources/consumer/expect) читаются из свойств.
# Артефакты: build/<testid>/; для multi-source тестов — build/<testid>/<basename>/.
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

no_test=false

flag_debug=false
flag_release=false
while [ $# -gt 0 ]; do
    case "$1" in
        --debug)   flag_debug=true; shift ;;
        --release) flag_release=true; shift ;;
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

CONFIGS="debug release"
if $flag_debug && ! $flag_release; then CONFIGS="debug"; fi
if $flag_release && ! $flag_debug; then CONFIGS="release"; fi

if [ -z "$testid" ]; then
    log_error "usage: $0 <testid> [--debug|--release] [--no-test]"
    exit 1
fi

if ! test_exists "$testid"; then
    log_error "unknown test id: '$testid' (valid: $TEST_IDS)"
    exit 1
fi

log_info "build-test: $testid (no-test=$no_test)"

RUNTIMECONFIG_JSON='{"runtimeOptions":{"tfm":"net10.0","framework":{"name":"Microsoft.NETCore.App","version":"10.0.11"},"rollForward":"Major"}}'

# --- type=gradle-image: особый путь — образ строит Gradle-тест dotnetutils ---
if [ "$(test_type "$testid")" = "gradle-image" ]; then
    OUTDIR="build/$testid"
    EXE="$OUTDIR/hello.exe"
    GRADLE_IMG="kotlin-dotnet-engine/dotnetutils/build/hello-image/hello.exe"
    mkdir -p "$OUTDIR"

    if ! $no_test || [ ! -f "$EXE" ]; then
        log_info "building PE image via :dotnetutils:test --tests '*HelloWorldImageTests*'"
        (cd "$PROJECT_ROOT/kotlin-dotnet-engine" && ./gradlew :dotnetutils:test \
            --tests "*HelloWorldImageTests*" --rerun-tasks -q)
        require_file "$GRADLE_IMG" "Gradle test did not produce $GRADLE_IMG"
        cp "$GRADLE_IMG" "$EXE"
    fi

    printf '%s\n' "$RUNTIMECONFIG_JSON" > "$OUTDIR/hello.runtimeconfig.json"

    if $no_test; then
        log_info "done: $testid (no-test)"
        exit 0
    fi

    out="$(timeout 30 dotnet "$EXE" 2>/dev/null || true)"
    expected="$(printf '%b' "$(test_prop "$testid" expect)")"
    if [ "$out" != "$expected" ]; then
        echo "FAIL: $testid"
        echo "--- expected ---"; echo "$expected"
        echo "--- got ---"; echo "$out"
        exit 1
    fi
    echo ">>> $testid run OK"

    log_info "verifying with C# harness (kotlin-dotnet-utils/verifier, real SRM)"
    ( cd kotlin-dotnet-utils/verifier && timeout 120 dotnet run --no-launch-profile -- \
        "$PROJECT_ROOT/$EXE" | grep -q "VERIFIER OK" ) \
        || { echo "FAIL: $testid (verifier)"; exit 1; }
    echo ">>> $testid verifier OK"

    log_info ">>> $testid OK"
    exit 0
fi

# --- Плагин JAR ---
PLUGIN_JAR="kotlin-dotnet-engine/compiler-plugin/build/libs/dotnet-compiler-plugin-0.1.0-SNAPSHOT.jar"
if [ ! -f "$PLUGIN_JAR" ]; then
    log_info "plugin JAR missing, building via gradlew..."
    (cd "$PROJECT_ROOT/kotlin-dotnet-engine" && ./gradlew :compiler-plugin:jar -q)
fi
require_file "$PLUGIN_JAR" "plugin JAR not found: $PLUGIN_JAR (run 'just plugin')"

# _pe_build_one <cfg> <tid> <kind> <kt> - compile one .kt via the PE backend
# into build/<tid>/<cfg>/ (multi-source tests distinguish artifacts by the
# source file name). No incrementality: PE is always rebuilt.
_pe_build_one() {
    local cfg="$1"
    local tid="$2"
    local kind="$3"
    local kt="$4"
    local name outdir asm
    name="$(basename "$kt" .kt)"
    outdir="build/$tid/$cfg"
    mkdir -p "$outdir/kt-out"

    log_info "compiling $kt → $outdir/$name.$kind (config=$cfg)"
    kotlinc -Xplugin="$PLUGIN_JAR" \
        -P "plugin:kotlin.dotnet:output.dir=$outdir" \
        -P "plugin:kotlin.dotnet:output.kind=$kind" \
        -P "plugin:kotlin.dotnet:config=$cfg" \
        "$kt" -d "$outdir/kt-out"

    asm="$outdir/$name.$kind"
    require_file "$asm" "plugin did not produce $asm"

    if [ "$kind" = "exe" ]; then
        local runtime_dll=""
        for cand in \
            runtime/bin/Release/net10.0/KotlinDotnetRuntime.dll \
            runtime/bin/Debug/net10.0/KotlinDotnetRuntime.dll; do
            if [ -f "$cand" ]; then runtime_dll="$cand"; break; fi
        done
        [ -n "$runtime_dll" ] || { log_error "KotlinDotnetRuntime.dll not found (run 'just runtime')"; exit 1; }
        cp "$runtime_dll" "$outdir/"
        printf '%s\n' "$RUNTIMECONFIG_JSON" > "$outdir/$name.runtimeconfig.json"
    fi

    if $no_test; then return 0; fi

    # SRM: the assembly must open with the real reader (verifier)...
    ( cd kotlin-dotnet-utils/verifier && timeout 120 dotnet run --no-launch-profile -- \
        "$PROJECT_ROOT/$asm" | grep -q "VERIFIER OK" ) \
        || { echo "FAIL: $tid [$cfg] $name (assembly verifier)"; exit 1; }

    # ...and the sidecar PDB through FromPortablePdbStream (pdbcheck).
    local pdb="$outdir/$name.pdb"
    require_file "$pdb" "missing sidecar PDB for $name ($cfg)"
    ( cd kotlin-dotnet-utils/pdbcheck && timeout 120 dotnet run --no-launch-profile --no-build -- \
        "$PROJECT_ROOT/$pdb" | grep -q "PDB OK" ) \
        || { echo "FAIL: $tid [$cfg] $name (pdb readable)"; exit 1; }
}

# Build all sources of a test into a single configuration.
_pe_build_config() {
    local cfg="$1"
    local tid="$2"
    local kind
    kind="$(test_kind "$tid")"
    while IFS= read -r kt; do
        _pe_build_one "$cfg" "$tid" "$kind" "$kt"
    done < <(test_kt "$tid")
}

# _verify_config <tid> <cfg> - run artifacts of one configuration.
# exe: run each image; dll: copy to the consumer's legacy path and run it.
_verify_config() {
    local tid="$1"
    local cfg="$2"
    local kind kt name expected expected_raw out

    kind="$(test_kind "$tid")"
    expected_raw="$(test_prop "$tid" expect "" || true)"
    expected="$(printf '%b' "$expected_raw")"

    if [ "$kind" = "dll" ]; then
        local consumer
        consumer="$(test_consumer "$tid")"
        [ -n "$consumer" ] || { echo "FAIL: $tid (dll without consumer in test.properties)"; exit 1; }
        local first_name
        first_name="$(basename "$(test_kt "$tid" | head -n 1)" .kt)"
        cp "build/$tid/$cfg/$first_name.dll" "build/$tid/$first_name.dll"
        out="$( cd "$consumer" && timeout 180 dotnet run 2>/dev/null || true )"
    else
        while IFS= read -r kt; do
            name="$(basename "$kt" .kt)"
            out="$(timeout 30 dotnet "build/$tid/$cfg/$name.exe" 2>/dev/null || true)"
            [ "$kind" = "exe" ] || break
        done < <(test_kt "$tid")
    fi

    if [ -n "$expected" ] && [ "$out" != "$expected" ]; then
        echo "FAIL: $tid [$cfg]"
        echo "--- expected ---"; echo "$expected"
        echo "--- got ---"; echo "$out"
        exit 1
    elif [ -z "$expected" ] && [ -z "$out" ]; then
        echo "FAIL: $tid [$cfg] (empty output)"
        exit 1
    fi
}

# --- Тело: сборка теста (см. docs/test-format.md) ---
case " $(test_backends "$testid") " in
    *" il "*)
        log_error "test '$testid': backends=il is not supported anymore (IL-text/ilasm path removed, see ADR 0012). Fix test.properties."
        exit 1 ;;
esac



if [ "$(test_kind "$testid")" != "exe" ] && [ "$(test_kind "$testid")" != "dll" ]; then
    log_error "test '$testid': unknown kind '$(test_kind "$testid")'"
    exit 1
fi

for cfg in $CONFIGS; do
    if $no_test; then
        while IFS= read -r kt; do
            _pe_build_one "$cfg" "$testid" "$(test_kind "$testid")" "$kt"
        done < <(test_kt "$testid")
    else
        _pe_build_config "$cfg" "$testid"
        _verify_config "$testid" "$cfg"
        echo ">>> $testid [$cfg] OK"
    fi
done

if $no_test; then
    log_info "done: $testid (no-test)"
else
    log_info ">>> $testid OK"
fi
