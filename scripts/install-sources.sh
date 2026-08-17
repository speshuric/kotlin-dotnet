#!/usr/bin/env bash
# scripts/install-sources.sh — shallow-clone исходников для референса в .sources/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCES_DIR="$PROJECT_ROOT/.sources"

KOTLIN_TAG="v2.4.20-RC"
DOTNET_BRANCH="release/10.0"

mkdir -p "$SOURCES_DIR"

# --- JetBrains/kotlin ---
if [ -d "$SOURCES_DIR/kotlin/.git" ]; then
  echo ">>> kotlin sources already cloned: $(git -C "$SOURCES_DIR/kotlin" rev-parse --short HEAD)"
else
  echo ">>> Cloning JetBrains/kotlin (shallow, $KOTLIN_TAG) ..."
  rm -rf "$SOURCES_DIR/kotlin"
  git clone --depth=1 --branch "$KOTLIN_TAG" https://github.com/JetBrains/kotlin.git "$SOURCES_DIR/kotlin"
fi

# --- dotnet/runtime ---
if [ -d "$SOURCES_DIR/dotnet-runtime/.git" ]; then
  echo ">>> dotnet-runtime sources already cloned: $(git -C "$SOURCES_DIR/dotnet-runtime" rev-parse --short HEAD)"
else
  echo ">>> Cloning dotnet/runtime (shallow, $DOTNET_BRANCH) ..."
  rm -rf "$SOURCES_DIR/dotnet-runtime"
  git clone --depth=1 --branch "$DOTNET_BRANCH" https://github.com/dotnet/runtime.git "$SOURCES_DIR/dotnet-runtime"
fi

echo ">>> Done."
