#!/usr/bin/env bash
# scripts/build-test-add.sh — полный конвейер для test-projects/00-int-add
#
# 1. Собирает компилятор-плагин (Gradle).
# 2. Запускает kotlinc с плагином на Arithmetic.kt → build/Arithmetic.il
# 3. Вызывает ilasm → build/Arithmetic.dll
# 4. Запускает C#-consumer (dotnet run) → печатает "5"
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# shellcheck disable=SC1091
source "$SCRIPT_DIR/activate.sh"

echo "=== Step 1: Build compiler plugin JAR ==="
cd "$PROJECT_ROOT"
./gradlew :compiler-plugin:jar
PLUGIN_JAR="compiler-plugin/build/libs/dotnet-compiler-plugin-0.1.0-SNAPSHOT.jar"

echo ""
echo "=== Step 2: Run kotlinc with plugin → Arithmetic.il ==="
mkdir -p build/kt-out
rm -f build/ir-dump.txt build/Arithmetic.il
kotlinc \
    -Xplugin="$PLUGIN_JAR" \
    test-projects/00-int-add/Arithmetic.kt \
    -d build/kt-out

echo ""
echo "=== Step 3: ilasm → Arithmetic.dll ==="
ilasm /dll /output:build/Arithmetic.dll build/Arithmetic.il

echo ""
echo "=== Step 4: Run C# consumer (expects: 5) ==="
cd test-projects/00-int-add/csharp-test
dotnet run

echo ""
echo "=== Done. Pipeline succeeded. ==="
