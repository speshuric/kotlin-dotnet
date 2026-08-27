#!/usr/bin/env bash
# scripts/show.sh — shows an IR dump / disassembled assembly.
#
# Usage:
#   show.sh <kind> <selector>
#     kind     ∈ ir | disasm
#     selector ∈ last | all | <testid> | <glob>
#
# Examples:
#   show.sh disasm last        # ildasm the newest assembly (.dll/.exe)
#   show.sh ir last            # the newest ir-dump
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

ensure_env
PROJECT_ROOT="${PROJECT_ROOT:-$KOTLIN_DOTNET_PROJECT_ROOT}"
cd "$PROJECT_ROOT"

# --- DSH prelude: writable HOME/GRADLE/XDG (needed by dotnet-ildasm) ---
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
    log_error "usage: $0 <kind: ir|disasm> <selector: last|all|<testid>|<glob>>"
    exit 1
fi

# _pattern_for_kind — file glob pattern for the given kind.
_pattern_for_kind() {
    case "$1" in
        ir)     echo 'ir-dump-*.txt' ;;
        disasm) echo '*.dll' ;;
        *) return 1 ;;
    esac
}

# _find_files <dir> <pattern> — print files in dir (recursive for 04-loops-spec).
# For disasm we also pick up .exe files (exe tests have no .dll).
_find_files() {
    local dir="$1"
    local pat="$2"
    if [ -d "$dir" ]; then
        # maxdepth 2: covers build/<id>/* and build/04-loops-spec/<sub>/*
        if [ "$pat" = "*.dll" ] && [ "${kind:-}" = "disasm" ]; then
            find "$dir" -maxdepth 2 -type f \( -name '*.dll' -o -name '*.exe' \) 2>/dev/null | sort
        else
            find "$dir" -maxdepth 2 -type f -name "$pat" 2>/dev/null | sort
        fi
    fi
}

# _filter_disasm — for disasm, drop the runtime DLL (not our code).
_filter_disasm() {
    if [ "$kind" = "disasm" ]; then
        grep -v 'KotlinDotnetRuntime\.dll$' || true
    else
        cat
    fi
}

# _resolve_files — collect the list of files for selector + kind.
_resolve_files() {
    local pattern
    pattern="$(_pattern_for_kind "$kind")" || { log_error "unknown kind: '$kind'"; exit 1; }

    case "$selector" in
        last)
            # Find the newest file by mtime across all build/*/<pattern>.
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
            # Exact testid or glob.
            if test_exists "$selector"; then
                _find_files "build/$selector" "$pattern" | _filter_disasm
            else
                # glob over ids → collect dirs.
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

# --- Action ---
case "$kind" in
    ir)
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
        # dotnet-ildasm is a global tool; it requires the activated environment.
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
        log_error "unknown kind: '$kind' (expected: ir | disasm)"
        exit 1
        ;;
esac
