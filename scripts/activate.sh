# scripts/activate.sh — activates the project's local environment
#
# Usage:
#   source scripts/activate.sh
#
# To clear the env: source scripts/deactivate.sh (or open a new shell).

_activate_dotnet() {
  # Portable script_dir: works in bash, zsh, and sh
  local script_dir
  if [ -n "${BASH_SOURCE[0]:-}" ]; then
    # bash
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  elif [ -n "${ZSH_VERSION:-}" ]; then
    # zsh: %x expands to the path of the file currently being sourced
    script_dir="$(cd "$(dirname "${(%):-%x}")" && pwd)"
  else
    # fallback: sh, where $0 is usually the path to this file
    script_dir="$(cd "$(dirname "$0")" && pwd)"
  fi
  local project_root
  project_root="$(cd "$script_dir/.." && pwd)"

  export KOTLIN_DOTNET_PROJECT_ROOT="$project_root"

  # JDK
  export JAVA_HOME="$project_root/.sdk/jdk"
  if [ -d "$JAVA_HOME/bin" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
  fi

  # .NET + global tools (dotnet-ildasm)
  export DOTNET_ROOT="$project_root/.sdk/dotnet"
  export DOTNET_CLI_TELEMETRY_OPTOUT=1
  export DOTNET_NOLOGO=1
  # dotnet-ildasm is built against an older .NET; only 10 is installed here → allow roll-forward
  export DOTNET_ROLL_FORWARD=Major
  if [ -d "$DOTNET_ROOT" ]; then
    export PATH="$DOTNET_ROOT:$DOTNET_ROOT/tools:$PATH"
  fi

  # kotlinc
  export KOTLIN_HOME="$project_root/.sdk/kotlinc"
  if [ -d "$KOTLIN_HOME/bin" ]; then
    export PATH="$KOTLIN_HOME/bin:$PATH"
  fi

  # Gradle
  if [ -d "$project_root/.sdk/gradle/bin" ]; then
    export PATH="$project_root/.sdk/gradle/bin:$PATH"
  fi

  echo "[kotlin-dotnet] env activated"
  echo "  JAVA_HOME=$JAVA_HOME"
  echo "  DOTNET_ROOT=$DOTNET_ROOT"
  echo "  KOTLIN_HOME=$KOTLIN_HOME"
}

_activate_dotnet
unset -f _activate_dotnet
