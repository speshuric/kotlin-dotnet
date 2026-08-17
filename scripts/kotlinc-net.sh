#!/usr/bin/env bash
# scripts/kotlinc-net.sh — компилятор Kotlin → .NET (PoC).
#
# Использование:
#   ./scripts/kotlinc-net.sh <file.kt>              # → build/<name>.exe
#   ./scripts/kotlinc-net.sh <file.kt> -dll         # → build/<name>.dll
#   ./scripts/kotlinc-net.sh <file.kt> -o out.exe   # явное имя выхода
#   ./scripts/kotlinc-net.sh <file.kt> --rebuild-plugin  # пересобрать плагин
#
# Требует: source scripts/activate.sh (или переменные окружения).
# Plugin JAR и KotlinDotnetRuntime.dll должны быть собраны заранее
# (just plugin && just runtime), если не указан --rebuild-plugin.
set -euo pipefail

# --- Разбор аргументов ---
kt_file=""
output=""
mode="exe"
rebuild_plugin=false

while [ $# -gt 0 ]; do
  case "$1" in
    -dll)      mode="dll"; shift ;;
    -exe)      mode="exe"; shift ;;
    -o)        output="$2"; shift 2 ;;
    --rebuild-plugin) rebuild_plugin=true; shift ;;
    -h|--help)
      sed -n '2,/^$/p' "$0" | sed 's/^# \?//'
      exit 0 ;;
    *) kt_file="$1"; shift ;;
  esac
done

if [ -z "$kt_file" ]; then
  echo "usage: $0 <file.kt> [-dll] [-o <out>] [--rebuild-plugin]" >&2
  exit 1
fi

if [ ! -f "$kt_file" ]; then
  echo "error: file not found: $kt_file" >&2
  exit 1
fi

# --- Окружение ---
# Активируем, если ещё не активировано (KOTLIN_DOTNET_PROJECT_ROOT не задан).
if [ -z "${KOTLIN_DOTNET_PROJECT_ROOT:-}" ]; then
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  # shellcheck disable=SC1091
  source "$script_dir/activate.sh"
fi
PROJECT_ROOT="$KOTLIN_DOTNET_PROJECT_ROOT"
cd "$PROJECT_ROOT"

# --- Plugin JAR ---
PLUGIN_JAR="compiler-plugin/build/libs/dotnet-compiler-plugin-0.1.0-SNAPSHOT.jar"
if $rebuild_plugin || [ ! -f "$PLUGIN_JAR" ]; then
  echo "[kotlinc-net] building compiler plugin..."
  ./gradlew :compiler-plugin:jar -q
fi
if [ ! -f "$PLUGIN_JAR" ]; then
  echo "error: plugin JAR not found: $PLUGIN_JAR" >&2
  echo "       run 'just plugin' or pass --rebuild-plugin" >&2
  exit 1
fi

# --- Имя выхода ---
name="$(basename "$kt_file" .kt)"
if [ -z "$output" ]; then
  output="build/${name}.${mode}"
fi
out_dir="$(dirname "$output")"
mkdir -p "$out_dir"

# --- Шаг 1: kotlinc + plugin → .il ---
il_file="build/${name}.il"
echo "[kotlinc-net] compiling $kt_file → $il_file"
mkdir -p build/kt-out
rm -f build/ir-dump.txt "$il_file"
kotlinc -Xplugin="$PLUGIN_JAR" "$kt_file" -d build/kt-out

if [ ! -f "$il_file" ]; then
  echo "error: plugin did not produce $il_file" >&2
  exit 1
fi

# --- Шаг 2: ilasm → .exe/.dll ---
echo "[kotlinc-net] assembling → $output"
ilasm "/${mode}" "/output:$output" "$il_file"

# --- Шаг 3 (только EXE): runtime DLL + runtimeconfig.json ---
if [ "$mode" = "exe" ]; then
  runtime_dll="runtime/bin/Release/net10.0/KotlinDotnetRuntime.dll"
  if [ ! -f "$runtime_dll" ]; then
    echo "error: KotlinDotnetRuntime.dll not found at $runtime_dll" >&2
    echo "       run 'just runtime' first" >&2
    exit 1
  fi
  # Копируем runtime DLL рядом с EXE (для разрешения сборок).
  cp "$runtime_dll" "$out_dir/"
  # Генерируем runtimeconfig.json (framework-dependent, rollForward Major).
  config_name="$(basename "$output" .exe).runtimeconfig.json"
  printf '%s\n' \
    '{"runtimeOptions":{"tfm":"net10.0","framework":{"name":"Microsoft.NETCore.App","version":"10.0.11"},"rollForward":"Major"}}' \
    > "$out_dir/$config_name"
  echo "[kotlinc-net] runtime: $(basename "$runtime_dll") + $config_name"
fi

echo "[kotlinc-net] done: $output"
if [ "$mode" = "exe" ]; then
  echo "[kotlinc-net] run:    dotnet $output"
fi
