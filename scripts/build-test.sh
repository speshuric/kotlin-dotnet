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

# _build_one <kt> <testid>  — собрать и (если не no-test) верифицировать
# один .kt файл. outdir = build/<testid>; для multi-source тестов —
# build/<testid>/<basename>/ (чтобы артефакты не перетирались).
_build_one() {
    local kt="$1"
    local tid="$2"
    local name kind outdir il asm sources_count

    name="$(basename "$kt" .kt)"
    kind="$(test_kind "$tid")"

    sources_count="$(test_kt "$tid" | wc -l)"
    if [ "$sources_count" -gt 1 ]; then
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
            -P "plugin:kotlin.dotnet:backend=il" \
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
# exe: прогон собранного образа; dll: запуск C#-consumer'а.
# Ожидание — ключ expect (printf %b); без него — проверка непустоты.
_verify() {
    local tid="$1"
    local name="$2"
    local asm="$3"
    local out expected consumer kind

    kind="$(test_kind "$tid")"
    if [ "$kind" = "dll" ]; then
        consumer="$(test_consumer "$tid")"
        [ -n "$consumer" ] || { echo "FAIL: $tid (dll without consumer in test.properties)"; exit 1; }
        # dotnet run печатает рантайм-шум в stderr; программа — в stdout.
        out="$(cd "$consumer" && timeout 180 dotnet run 2>/dev/null || true)"
    else
        out="$(timeout 30 dotnet "$asm" 2>/dev/null || true)"
    fi

    expected="$(test_prop "$tid" expect "" || true)"
    if [ -n "$expected" ]; then
        expected="$(printf '%b' "$expected")"
        if [ "$out" != "$expected" ]; then
            echo "FAIL: $tid"
            echo "--- expected ---"
            echo "$expected"
            echo "--- got ---"
            echo "$out"
            exit 1
        fi
    elif [ -z "$out" ]; then
        echo "FAIL: $tid (empty output)"
        exit 1
    fi
    echo ">>> $tid/$name OK"
}

# --- S5 (ADR 0010): второй прогон на pe-бэкенде ---
# Артефакты — в build/<tid>/pe/, ожидания те же, что на il-пути.
# Для dll-тестов (00-int-add, 02-expr) pe-DLL временно подменяет
# il-DLL по пути из HintPath consumer'а, затем восстанавливается.

_pe_build_one() {
    local kt="$1"
    local tid="$2"
    local kind="$3"
    local name outdir
    name="$(basename "$kt" .kt)"
    outdir="build/$tid/pe"
    mkdir -p "$outdir/kt-out"

    log_info "pe: compiling $kt → $outdir/$name.$kind"
    kotlinc -Xplugin="$PLUGIN_JAR" \
        -P "plugin:kotlin.dotnet:output.dir=$outdir" \
        -P "plugin:kotlin.dotnet:backend=pe" \
        -P "plugin:kotlin.dotnet:output.kind=$kind" \
        "$kt" -d "$outdir/kt-out"

    local asm="$outdir/$name.$kind"
    require_file "$asm" "pe backend did not produce $asm"

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

    # C#-harness: pe-артефакт должен открываться настоящим SRM.
    if $no_test; then return 0; fi
    ( cd kotlin-dotnet-utils/verifier && timeout 120 dotnet run --no-launch-profile -- \
        "$PROJECT_ROOT/$asm" | grep -q "VERIFIER OK" ) \
        || { echo "FAIL: $tid pe (verifier)"; exit 1; }
}

_verify_pe() {
    local tid="$1"
    local kind kt name expected expected_raw out consumer_dll backup

    kind="$(test_kind "$tid")"

    # Сборка всех исходников теста на pe-бэкенде (+ verifier на каждый).
    while IFS= read -r kt; do
        _pe_build_one "$kt" "$tid" "$kind"
    done < <(test_kt "$tid")

    if [ "$kind" = "dll" ]; then
        # Подмена DLL по пути из HintPath consumer'а с восстановлением
        # (одна dll на тест; multi-source dll-тестов пока нет).
        name="$(basename "$(test_kt "$tid" | head -n 1)" .kt)"
        consumer_dll="build/$tid/$name.dll"
        backup="build/$tid/${name}.il-dll.bak"
        cp "$consumer_dll" "$backup"
        cp "build/$tid/pe/$name.dll" "$consumer_dll"
        out="$( cd "$(test_consumer "$tid")" && timeout 180 dotnet run 2>/dev/null || true )"
        mv -f "$backup" "$consumer_dll"

        expected_raw="$(test_prop "$tid" expect "" || true)"
        if [ -n "$expected_raw" ]; then
            expected="$(printf '%b' "$expected_raw")"
            if [ "$out" != "$expected" ]; then
                echo "FAIL: $tid pe"
                echo "--- expected ---"; echo "$expected"
                echo "--- got ---"; echo "$out"
                exit 1
            fi
        elif [ -z "$out" ]; then
            echo "FAIL: $tid pe (empty output)"
            exit 1
        fi
    else
        expected="$(printf '%b' "$(test_prop "$tid" expect)")"
        while IFS= read -r kt; do
            name="$(basename "$kt" .kt)"
            out="$(timeout 30 dotnet "build/$tid/pe/$name.exe" 2>/dev/null || true)"
            if [ "$out" != "$expected" ]; then
                echo "FAIL: $tid pe ($name)"
                echo "--- expected ---"; echo "$expected"
                echo "--- got ---"; echo "$out"
                exit 1
            fi
        done < <(test_kt "$tid")
    fi
    echo ">>> $tid pe OK"
}

# --- Тело: сборка по бэкендам из свойств теста (см. docs/test-format.md) ---
case " $(test_backends "$testid") " in
    *" il "*) do_il=true ;;
    *) do_il=false ;;
esac
case " $(test_backends "$testid") " in
    *" pe "*) do_pe=true ;;
    *) do_pe=false ;;
esac

if $do_il; then
    while IFS= read -r kt; do
        _build_one "$kt" "$testid"
    done < <(test_kt "$testid")
fi

if $do_pe; then
    if $no_test; then
        # Только собрать pe-артефакты (без прогона и verifier).
        while IFS= read -r kt; do
            _pe_build_one "$kt" "$testid" "$(test_kind "$testid")"
        done < <(test_kt "$testid")
    else
        _verify_pe "$testid"
    fi
fi

if $no_test; then
    log_info "done: $testid (no-test)"
else
    log_info ">>> $testid OK"
fi
