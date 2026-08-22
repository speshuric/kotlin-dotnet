#!/usr/bin/env bash
# scripts/kotlinc-net.sh — компилятор Kotlin → .NET (PoC).
#
# Использование:
#   ./scripts/kotlinc-net.sh <file.kt>              # → build/<name>/<name>.exe
#   ./scripts/kotlinc-net.sh <file.kt> -dll         # → build/<name>/<name>.dll
#   ./scripts/kotlinc-net.sh <file.kt> -o out.exe   # явное имя выхода
#   ./scripts/kotlinc-net.sh <file.kt> -pe            # backend=pe (без ilasm, ADR 0010)
#   ./scripts/kotlinc-net.sh <file.kt> --rebuild-plugin  # пересобрать плагин
#
# Артефакты (.il, ir-dump, .exe/.dll) складываются в build/<name>/
# (per-test layout). Plugin JAR и KotlinDotnetRuntime.dll должны быть
# собраны заранее (just plugin && just runtime), если не указан
# --rebuild-plugin.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

# --- DSH prelude: writable HOME/GRADLE/XDG (read-only корневая ФС) ---
ensure_env
PROJECT_ROOT="${KOTLIN_DOTNET_PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
export GRADLE_USER_HOME="$PROJECT_ROOT/build/tmp/gradle-home"
export XDG_RUNTIME_DIR="$PROJECT_ROOT/build/tmp/runtime"
export HOME="$PROJECT_ROOT/build/tmp/home"
export DOTNET_CLI_HOME="$HOME"
mkdir -p "$GRADLE_USER_HOME" "$XDG_RUNTIME_DIR" "$HOME"
cd "$PROJECT_ROOT"

# --- Разбор аргументов ---
kt_file=""
output=""
mode="exe"
rebuild_plugin=false
pe_backend=false

while [ $# -gt 0 ]; do
  case "$1" in
    -dll)      mode="dll"; shift ;;
    -exe)      mode="exe"; shift ;;
    -pe)       pe_backend=true; shift ;;
    -o)        output="$2"; shift 2 ;;
    --rebuild-plugin) rebuild_plugin=true; shift ;;
    -h|--help)
      sed -n '2,/^$/p' "$0" | sed 's/^# \?//'
      exit 0 ;;
    *) kt_file="$1"; shift ;;
  esac
done

if [ -z "$kt_file" ]; then
  log_error "usage: $0 <file.kt> [-dll] [-o <out>] [--rebuild-plugin]"
  exit 1
fi

require_file "$kt_file" "error: file not found: $kt_file"

# --- Plugin JAR ---
PLUGIN_JAR="kotlin-dotnet-engine/compiler-plugin/build/libs/dotnet-compiler-plugin-0.1.0-SNAPSHOT.jar"
if $rebuild_plugin || [ ! -f "$PLUGIN_JAR" ]; then
  log_info "building compiler plugin..."
  (cd "$PROJECT_ROOT/kotlin-dotnet-engine" && ./gradlew :compiler-plugin:jar -q)
fi
require_file "$PLUGIN_JAR" "plugin JAR not found: $PLUGIN_JAR (run 'just plugin' or pass --rebuild-plugin)"

# --- Имя выхода + per-test outdir ---
name="$(basename "$kt_file" .kt)"
outdir="build/${name}"
if [ -z "$output" ]; then
  output="$outdir/${name}.${mode}"
fi
out_dir="$(dirname "$output")"
mkdir -p "$out_dir" "$outdir/kt-out"

# --- Шаг 1: kotlinc + plugin → .il (il-путь) или .exe/.dll (pe-путь, ADR 0010) ---
if $pe_backend; then
  log_info "compiling $kt_file → $output (backend=pe)"
  kotlinc -Xplugin="$PLUGIN_JAR" \
    -P "plugin:kotlin.dotnet:output.dir=$outdir" \
    -P "plugin:kotlin.dotnet:backend=pe" \
    -P "plugin:kotlin.dotnet:output.kind=$mode" \
    "$kt_file" -d "$outdir/kt-out"
  # Плагин пишет в per-test layout ($outdir/$name.$kind); при явном
  # -o переносим на запрошенный путь.
  if [ "$output" != "$outdir/${name}.${mode}" ]; then
    require_file "$outdir/${name}.${mode}" "plugin did not produce $outdir/${name}.${mode}"
    mkdir -p "$out_dir"
    mv "$outdir/${name}.${mode}" "$output"
  fi
  require_file "$output" "plugin did not produce $output"
else
  il_file="$outdir/${name}.il"
  log_info "compiling $kt_file → $il_file"
  rm -f "$outdir"/ir-dump-*.txt "$il_file"
  kotlinc -Xplugin="$PLUGIN_JAR" \
    -P "plugin:kotlin.dotnet:output.dir=$outdir" \
    "$kt_file" -d "$outdir/kt-out"

  require_file "$il_file" "plugin did not produce $il_file"

  # --- Шаг 2: ilasm → .exe/.dll ---
  log_info "assembling → $output"
  ilasm "/${mode}" "/output:$output" "$il_file"
fi

# --- Шаг 3 (только EXE): runtime DLL + runtimeconfig.json ---
if [ "$mode" = "exe" ]; then
  runtime_dll="runtime/bin/Release/net10.0/KotlinDotnetRuntime.dll"
  require_file "$runtime_dll" "KotlinDotnetRuntime.dll not found at $runtime_dll (run 'just runtime' first)"
  # Копируем runtime DLL рядом с EXE (для разрешения сборок).
  cp "$runtime_dll" "$out_dir/"
  # Генерируем runtimeconfig.json (framework-dependent, rollForward Major).
  config_name="$(basename "$output" .exe).runtimeconfig.json"
  printf '%s\n' \
    '{"runtimeOptions":{"tfm":"net10.0","framework":{"name":"Microsoft.NETCore.App","version":"10.0.11"},"rollForward":"Major"}}' \
    > "$out_dir/$config_name"
  log_info "runtime: $(basename "$runtime_dll") + $config_name"
fi

log_info "done: $output"
if [ "$mode" = "exe" ]; then
  log_info "run:    dotnet $output"
fi
