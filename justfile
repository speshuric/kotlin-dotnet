# kotlin-dotnet — proof-of-concept компилятора Kotlin → .NET
# Полный конвейер: just bootstrap && just test

default: list

# Показать все рецепты.
list:
    @just --list

# === Окружение ===

# Установить локальные SDK (JDK, kotlinc, .NET, Gradle, dotnet-ildasm) в .sdk/.
sdks:
    ./scripts/install-sdks.sh

# Клонировать исходники для референса (JetBrains/kotlin, dotnet/runtime) в .sources/.
sources:
    ./scripts/install-sources.sh

# Полный бутстрап: SDK + исходники + проверка запускаемости.
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

# === Сборка ===

# Собрать весь проект (plugin + runtime + все тесты). config: debug (по умолчанию) | release.
build config="debug":
    ./scripts/build.sh all {{config}}

# Собрать без запуска тестов/верификации (только IL + DLL/EXE + runtime-copy).
build-no-test config="debug":
    ./scripts/build.sh all {{config}} --no-test

# Собрать только compiler-plugin JAR (Gradle).
plugin:
    ./scripts/build.sh plugin debug

# Собрать только KotlinDotnetRuntime.dll (C#).
runtime config="release":
    ./scripts/build.sh runtime {{config}}

# === Компиляция одного файла ===

# Скомпилировать .kt → .exe/.dll (CLI). Использование: just compile path/to/file.kt [-dll]
compile file:
    ./scripts/kotlinc-net.sh {{file}}

# === Тесты ===

# Запустить тесты. selector: all (по умолчанию) | <testid> | <glob>. just test == just test-all.
test selector="all":
    ./scripts/test.sh {{selector}}

# Запустить все тесты (синоним just test).
test-all:
    just test all

# Дымовой тест: один тест (00-int-add).
test-smoke:
    just test 00-int-add

# Синоним test-smoke.
test-short: test-smoke

# === Отладка ===

# Дизассемблировать DLL через dotnet-ildasm. selector: last | all | <testid> | <glob>.
disasm selector="last":
    ./scripts/show.sh disasm {{selector}}

# Синоним disasm (бывший show-il: IL теперь смотрят через ildasm дизассемблер).
show-il selector="last":
    @./scripts/show.sh disasm {{selector}}

# Показать IR-дамп из плагина. selector: last | all | <testid> | <glob>.
show-ir selector="last":
    ./scripts/show.sh ir {{selector}}

# === Очистка ===

# Очистка. category: all (по умолчанию) | build | sdk | sources. Bare clean = all (требует пере-bootstrap!).
clean category="all":
    ./scripts/clean.sh {{category}}
