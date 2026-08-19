#!/usr/bin/env bash
# scripts/install-sdks.sh — установка JDK 21, kotlinc 2.4.20-RC, .NET 10 SDK локально в .sdk/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"
SDK_DIR="$PROJECT_ROOT/.sdk"

mkdir -p "$SDK_DIR"

# --- Версии ---
JDK_VERSION="21"
JDK_VENDOR="temurin"
KOTLIN_VERSION="2.4.20-RC"
DOTNET_CHANNEL="10.0"
GRADLE_VERSION="9.7.0"

log_info "Installing local SDKs into $SDK_DIR"

# --- JDK (Temurin 21) ---
JDK_DIR="$SDK_DIR/jdk"
if [ -x "$JDK_DIR/bin/java" ]; then
  log_info "JDK already installed: $("$JDK_DIR/bin/java" -version 2>&1 | head -1)"
else
  log_info "Installing Temurin JDK $JDK_VERSION ..."
  # Adoptium API: последняя GA сборка для linux x64
  JDK_URL="https://api.adoptium.net/v3/binary/latest/${JDK_VERSION}/ga/linux/x64/jdk/hotspot/normal/eclipse"
  echo "    Download: $JDK_URL"
  tmp="$(mktemp -d)"
  curl -sSL -o "$tmp/jdk.tar.gz" "$JDK_URL"
  mkdir -p "$JDK_DIR"
  tar -xzf "$tmp/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
  rm -rf "$tmp"
  echo "    JDK installed: $("$JDK_DIR/bin/java" -version 2>&1 | head -1)"
fi

# --- kotlinc ---
KOTLIN_DIR="$SDK_DIR/kotlinc"
if [ -x "$KOTLIN_DIR/bin/kotlinc" ]; then
  log_info "kotlinc already installed: $("$KOTLIN_DIR/bin/kotlinc" -version 2>&1 | head -1)"
else
  log_info "Installing kotlinc $KOTLIN_VERSION ..."
  KOTLIN_URL="https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip"
  echo "    Download: $KOTLIN_URL"
  tmp="$(mktemp -d)"
  curl -sSL -o "$tmp/kotlinc.zip" "$KOTLIN_URL"
  mkdir -p "$KOTLIN_DIR"
  unzip -q "$tmp/kotlinc.zip" -d "$tmp/unpacked"
  # Структура: kotlinc/bin, kotlinc/lib — копируем содержимое
  cp -r "$tmp/unpacked/kotlinc/." "$KOTLIN_DIR/"
  rm -rf "$tmp"
  echo "    kotlinc installed: $("$KOTLIN_DIR/bin/kotlinc" -version 2>&1 | head -1)"
fi

# --- .NET SDK 10 ---
DOTNET_DIR="$SDK_DIR/dotnet"
if [ -x "$DOTNET_DIR/dotnet" ]; then
  log_info ".NET SDK already installed: $("$DOTNET_DIR/dotnet" --version 2>&1 | head -1)"
else
  log_info "Installing .NET SDK (channel $DOTNET_CHANNEL) ..."
  curl -sSL https://dot.net/v1/dotnet-install.sh -o "$SDK_DIR/dotnet-install.sh"
  chmod +x "$SDK_DIR/dotnet-install.sh"
  "$SDK_DIR/dotnet-install.sh" --channel "$DOTNET_CHANNEL" --install-dir "$DOTNET_DIR" --no-path
  echo "    .NET installed: $("$DOTNET_DIR/dotnet" --version 2>&1 | head -1)"

  # ilasm/ildasm не входят в .NET 10 SDK на Linux — ставим как global tools
  # в $DOTNET_DIR/tools/ (внутри локального .sdk/)
  TOOLS_DIR="$DOTNET_DIR/tools"
  mkdir -p "$TOOLS_DIR"
  export DOTNET_ROOT="$DOTNET_DIR"
  export PATH="$DOTNET_DIR:$PATH"
  if [ -x "$TOOLS_DIR/ilasm" ]; then
    echo "    ilasm-cli already installed"
  else
    echo "    Installing ilasm-cli (NuGet tool) ..."
    "$DOTNET_DIR/dotnet" tool install --tool-path "$TOOLS_DIR" ilasm-cli 2>/dev/null
  fi
  if [ -x "$TOOLS_DIR/dotnet-ildasm" ]; then
    echo "    dotnet-ildasm already installed"
  else
    echo "    Installing dotnet-ildasm (NuGet tool) ..."
    "$DOTNET_DIR/dotnet" tool install --tool-path "$TOOLS_DIR" dotnet-ildasm 2>/dev/null
  fi
fi

# --- Gradle ---
GRADLE_DIR="$SDK_DIR/gradle"
if [ -x "$GRADLE_DIR/bin/gradle" ]; then
  log_info "Gradle already installed: $("$GRADLE_DIR/bin/gradle" --version 2>&1 | grep -E '^Gradle ' | head -1)"
else
  log_info "Installing Gradle $GRADLE_VERSION ..."
  GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  echo "    Download: $GRADLE_URL"
  tmp="$(mktemp -d)"
  curl -sSL -o "$tmp/gradle.zip" "$GRADLE_URL"
  mkdir -p "$GRADLE_DIR"
  unzip -q "$tmp/gradle.zip" -d "$tmp/unpacked"
  cp -r "$tmp/unpacked/gradle-${GRADLE_VERSION}/." "$GRADLE_DIR/"
  rm -rf "$tmp"
  echo "    Gradle installed: $("$GRADLE_DIR/bin/gradle" --version 2>&1 | grep -E '^Gradle ' | head -1)"
fi

log_info "Done. Activate with: source scripts/activate.sh"
