# kotlin-dotnet — proof-of-concept Kotlin → .NET compiler
# Full pipeline: just bootstrap && just test

default: list

# Show all recipes.
list:
    @just --list

# === Environment ===

# Install local SDKs (JDK, kotlinc, .NET, Gradle, dotnet-ildasm) into .sdk/.
sdks:
    ./scripts/install-sdks.sh

# Clone reference sources (JetBrains/kotlin, dotnet/runtime) into .sources/.
sources:
    ./scripts/install-sources.sh

# Full bootstrap: SDKs + sources + runnability check.
bootstrap: sdks sources
    #!/usr/bin/env bash
    source scripts/activate.sh
    echo "=== Verify ==="
    echo "java:    $(java -version 2>&1 | head -1)"
    echo "kotlinc: $(kotlinc -version 2>&1 | head -1)"
    echo "dotnet:  $(dotnet --version 2>&1 | head -1)"
    echo "ildasm:  $(command -v dotnet-ildasm 2>&1 || echo NOT FOUND)"
    echo "gradle:  $(cd kotlin-dotnet-engine && ./gradlew --version 2>&1 | grep '^Gradle ' | head -1)"
    echo ""
    echo ">>> Bootstrap complete. Run 'just test' to verify pipeline."

# === Build ===

# Build the whole project (plugin + runtime + all tests). config: debug (default) | release.
build config="debug":
    ./scripts/build.sh all {{config}}

# Build without running tests/verification (only IL + DLL/EXE + runtime copy).
build-no-test config="debug":
    ./scripts/build.sh all {{config}} --no-test

# Build only the compiler plugin JAR (Gradle).
plugin:
    ./scripts/build.sh plugin debug

# Build only KotlinDotnetRuntime.dll (C#).
runtime config="release":
    ./scripts/build.sh runtime {{config}}

# === Compiling a single file ===

# Compile a .kt file to .exe/.dll (CLI). Usage: just compile path/to/file.kt [-dll]
compile file:
    ./scripts/kotlinc-net.sh {{file}}

# === Tests ===

# Run tests. selector: all (default) | <testid> | <glob>. just test == just test-all.
test selector="all":
    ./scripts/test.sh {{selector}}

# Run all tests (alias for just test).
test-all:
    just test all

# Smoke test: one test (00-int-add).
test-smoke:
    just test 00-int-add

# Alias for test-smoke.
test-short: test-smoke

# === Debugging ===

# Disassemble a DLL via dotnet-ildasm. selector: last | all | <testid> | <glob>.
disasm selector="last":
    ./scripts/show.sh disasm {{selector}}

# Alias for disasm (formerly show-il: IL is now viewed through the ildasm disassembler).
show-il selector="last":
    @./scripts/show.sh disasm {{selector}}

# Show the IR dump from the plugin. selector: last | all | <testid> | <glob>.
show-ir selector="last":
    ./scripts/show.sh ir {{selector}}

# Kill the Kotlin compile daemon: kotlinc can pick up a stale
# plugin jar after a rebuild (bit us on K-02). After --rerun-tasks
# or a manual jar rebuild, run this before compiling .kt files.
kill-daemon:
    pkill -f "[K]otlinCompileDaemon" || true

# === Cleanup ===

# Clean. category: all (default) | build | sdk | sources. Bare clean = all (re-bootstrap required!).
clean category="all":
    ./scripts/clean.sh {{category}}
