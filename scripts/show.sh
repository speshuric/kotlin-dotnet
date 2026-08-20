#!/usr/bin/env bash
# scripts/show.sh — показать сгенерированный IL / IR-дамп / дизассемблированный DLL.
#
# Использование:
#   show.sh <kind> <selector>
#     kind     ∈ il | ir | disasm
#     selector ∈ last | all | <testid> | <glob>
#
# Примеры:
#   show.sh il last            # новейший .il
#   show.sh il all             # все .il
#   show.sh il 04-loops        # build/04-loops/*.il
#   show.sh il 00*             # glob по id
#   show.sh disasm last        # ildasm новейшей .dll
#   show.sh ir last            # новейший ir-dump
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

ensure_env
PROJECT_ROOT="${PROJECT_ROOT:-$KOTLIN_DOTNET_PROJECT_ROOT}"
cd "$PROJECT_ROOT"

# --- DSH prelude: writable HOME/GRADLE/XDG (нужно для dotnet-ildasm) ---
export GRADLE_USER_HOME="$PROJECT_ROOT/build/tmp/gradle-home"
export XDG_RUNTIME_DIR="$PROJECT_ROOT/build/tmp/runtime"
export HOME="$PROJECT_ROOT/build/tmp/home"
export DOTNET_CLI_HOME="$HOME"
mkdir -p "$GRADLE_USER_HOME" "$XDG_RUNTIME_DIR" "$HOME"

# shellcheck disable=SC1091
source "$PROJECT_ROOT/scripts/tests.sh"

kind="${1:-}"
selector="${2:-last}"

if [ -z "$kind" ]; then
    log_error "usage: $0 <kind: il|ir|disasm> <selector: last|all|<testid>|<glob>>"
    exit 1
fi

# _pattern_for_kind — glob-шаблон файлов для данного kind.
_pattern_for_kind() {
    case "$1" in
        il)     echo '*.il' ;;
        ir)     echo 'ir-dump-*.txt' ;;
        disasm) echo '*.dll' ;;
        *) return 1 ;;
    esac
}

# _find_files <dir> <pattern> — напечатать файлы в dir (рекурсивно для 04-loops-spec).
_find_files() {
    local dir="$1"
    local pat="$2"
    if [ -d "$dir" ]; then
        # maxdepth 2: ловит build/<id>/*.il и build/04-loops-spec/<sub>/*.il
        find "$dir" -maxdepth 2 -type f -name "$pat" 2>/dev/null | sort
    fi
}

# _filter_disasm — для disasm убрать KotlinDotnetRuntime.dll (это не наш код).
_filter_disasm() {
    if [ "$kind" = "disasm" ]; then
        grep -v 'KotlinDotnetRuntime\.dll$' || true
    else
        cat
    fi
}

# _resolve_files — собрать список файлов по selector + kind.
_resolve_files() {
    local pattern
    pattern="$(_pattern_for_kind "$kind")" || { log_error "unknown kind: '$kind'"; exit 1; }

    case "$selector" in
        last)
            # Найти новейший файл по mtime во всех build/*/<pattern>.
            local newest=""
            local newest_mtime=0
            local dir f m
            for dir in build/*/ ; do
                [ -d "$dir" ] || continue
                while IFS= read -r f; do
                    [ -f "$f" ] || continue
                    case "$kind" in
                        disasm) case "$f" in *KotlinDotnetRuntime.dll) continue ;; esac ;;
                    esac
                    m=$(stat -c %Y "$f" 2>/dev/null || stat -f %m "$f" 2>/dev/null || echo 0)
                    if [ "$m" -gt "$newest_mtime" ]; then
                        newest_mtime="$m"
                        newest="$f"
                    fi
                done < <(_find_files "$dir" "$pattern")
            done
            if [ -n "$newest" ]; then
                echo "$newest"
            fi
            ;;
        all)
            local dir
            for dir in build/*/ ; do
                [ -d "$dir" ] || continue
                _find_files "$dir" "$pattern" | _filter_disasm
            done
            ;;
        *)
            # Точный testid или glob.
            if test_exists "$selector"; then
                _find_files "build/$selector" "$pattern" | _filter_disasm
            else
                # glob по id → собрать dirs.
                local matched ids
                matched="$(resolve_selector "$selector" 2>/dev/null || true)"
                if [ -n "$matched" ]; then
                    for id in $matched; do
                        _find_files "build/$id" "$pattern" | _filter_disasm
                    done
                fi
            fi
            ;;
    esac
}

files="$(_resolve_files)"

if [ -z "$files" ]; then
    log_error "no $kind files found for selector '$selector'"
    exit 1
fi

# --- Действие ---
case "$kind" in
    il|ir)
        shown_count=0
        while IFS= read -r f; do
            echo "=== $f ==="
            cat "$f"
            echo ""
            shown_count=$((shown_count + 1))
        done <<< "$files"
        log_info "shown $shown_count file(s) (kind=$kind, selector=$selector)"
        ;;
    disasm)
        # dotnet-ildasm — глобальный tool, требует активированное окружение.
        shown_count=0
        while IFS= read -r f; do
            echo "=== $f ==="
            dotnet-ildasm "$f"
            echo ""
            shown_count=$((shown_count + 1))
        done <<< "$files"
        log_info "disassembled $shown_count file(s) (selector=$selector)"
        ;;
    *)
        log_error "unknown kind: '$kind' (expected: il | ir | disasm)"
        exit 1
        ;;
esac
