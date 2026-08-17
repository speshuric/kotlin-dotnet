#!/usr/bin/env bash
# scripts/bootstrap.sh — полная установка: SDK + исходники + проверка
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "===================="
echo " Step 1: Install SDKs"
echo "===================="
"$SCRIPT_DIR/install-sdks.sh"

echo ""
echo "===================="
echo " Step 2: Install sources"
echo "===================="
"$SCRIPT_DIR/install-sources.sh"

echo ""
echo "===================="
echo " Step 3: Verify"
echo "===================="
# shellcheck disable=SC1090
source "$SCRIPT_DIR/activate.sh"

echo "java:   $(java -version 2>&1 | head -1)"
echo "kotlinc: $(kotlinc -version 2>&1 | head -1)"
echo "dotnet:  $(dotnet --version 2>&1 | head -1)"

if command -v ilasm >/dev/null 2>&1; then
  echo "ilasm:  $(command -v ilasm)"
else
  echo "ilasm:  NOT FOUND"
fi

if command -v dotnet-ildasm >/dev/null 2>&1; then
  echo "ildasm: $(command -v dotnet-ildasm)"
else
  echo "ildasm: NOT FOUND"
fi

if [ -d "$KOTLIN_DOTNET_PROJECT_ROOT/.sources/kotlin/compiler/ir" ]; then
  echo "kotlin sources: OK"
else
  echo "kotlin sources: MISSING"
fi

if [ -d "$KOTLIN_DOTNET_PROJECT_ROOT/.sources/dotnet-runtime/src/libraries" ]; then
  echo "dotnet sources: OK"
else
  echo "dotnet sources: MISSING"
fi

echo ""
echo ">>> Bootstrap complete. Activate env with: source scripts/activate.sh"
