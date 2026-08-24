# scripts/deactivate.sh — снять env, установленный activate.sh
#
# Использование:
#   source scripts/deactivate.sh

_deactivate_dotnet() {
  export PATH="${PATH//"$KOTLIN_DOTNET_PROJECT_ROOT\/.sdk\/jdk\/bin:"/}"
  export PATH="${PATH//"$KOTLIN_DOTNET_PROJECT_ROOT\/.sdk\/dotnet:"/}"
  export PATH="${PATH//"$KOTLIN_DOTNET_PROJECT_ROOT\/.sdk\/dotnet\/tools:"/}"
  export PATH="${PATH//"$KOTLIN_DOTNET_PROJECT_ROOT\/.sdk\/kotlinc\/bin:"/}"
  unset JAVA_HOME DOTNET_ROOT KOTLIN_HOME DOTNET_CLI_TELEMETRY_OPTOUT DOTNET_NOLOGO KOTLIN_DOTNET_PROJECT_ROOT
  unset -f ildasm _find_iltool 2>/dev/null
  echo "[kotlin-dotnet] env deactivated"
}

_deactivate_dotnet
unset -f _deactivate_dotnet
