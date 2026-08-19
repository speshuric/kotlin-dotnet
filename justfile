# kotlin-dotnet — proof-of-concept компилятора Kotlin → .NET
# Полный конвейер: just bootstrap && just test

# Дефолтная цель: показать список рецептов.
default: list

# Показать все рецепты (аналог `make help`).
list:
    @just --list

# === Окружение ===

# Установить локальные SDK (JDK 21, kotlinc 2.4.20-RC, .NET 10, Gradle 9.7.0,
# ilasm-cli, dotnet-ildasm) в .sdk/.
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
    echo "ilasm:   $(command -v ilasm 2>&1 || echo NOT FOUND)"
    echo "ildasm:  $(command -v dotnet-ildasm 2>&1 || echo NOT FOUND)"
    echo "gradle:  $(./gradlew --version 2>&1 | grep '^Gradle ' | head -1)"
    echo ""
    echo ">>> Bootstrap complete. Run 'just test-all' to verify pipeline."

# === Сборка compiler plugin ===

# Собрать compiler-plugin JAR через Gradle (инкрементально — Gradle UP-TO-DATE).
plugin:
    #!/usr/bin/env bash
    source scripts/activate.sh
    ./gradlew :compiler-plugin:jar

# === Универсальный pipeline (параметризуемый) ===

# Скомпилировать .kt → .exe (или .dll с флагом -dll).
# Использование: just compile path/to/file.kt
#                just compile path/to/file.kt -dll
compile file:
    ./scripts/kotlinc-net.sh {{file}}

# Внутренний рецепт: сгенерировать <name>.il из .kt файла.
_gen-il name kt:
    #!/usr/bin/env bash
    source scripts/activate.sh
    jar=compiler-plugin/build/libs/dotnet-compiler-plugin-0.1.0-SNAPSHOT.jar
    out=build/{{name}}.il
    if [ -f "$out" ] && [ "$out" -nt "{{kt}}" ] && [ "$out" -nt "$jar" ]; then
        echo "[just] $out up-to-date"
        exit 0
    fi
    echo "[just] generating $out"
    mkdir -p build/kt-out
    rm -f build/ir-dump-*.txt "$out"
    kotlinc -Xplugin="$jar" \
        -P "plugin:kotlin.dotnet:output.dir=build" \
        "{{kt}}" -d build/kt-out

# Внутренний рецепт: ilasm → <name>.dll (или .exe если указан /exe).
_gen-asm name exe="dll":
    #!/usr/bin/env bash
    source scripts/activate.sh
    il=build/{{name}}.il
    out=build/{{name}}.{{exe}}
    if [ -f "$out" ] && [ "$out" -nt "$il" ]; then
        echo "[just] $out up-to-date"
        exit 0
    fi
    echo "[just] assembling $out"
    ilasm /{{exe}} /output:"$out" "$il"

# === Runtime (KotlinDotnetRuntime) ===

# Собрать KotlinDotnetRuntime.dll (C# class library).
runtime:
    #!/usr/bin/env bash
    source scripts/activate.sh
    dotnet build runtime/ -c Release

# === Тестовые проекты ===

# 00-int-add: test_add(Int, Int): Int — минимальный pipeline.
test: test-00-int-add
    @echo ">>> just test (= test-00-int-add) OK"

test-00-int-add: plugin
    #!/usr/bin/env bash
    source scripts/activate.sh
    just _gen-il Arithmetic test-projects/00-int-add/Arithmetic.kt
    just _gen-asm Arithmetic dll
    cd test-projects/00-int-add/csharp-test && dotnet run

# 02-expr: арифметика, локальные переменные, if/when, сравнения, bool, Long/Double.
test-02-expr: plugin
    #!/usr/bin/env bash
    source scripts/activate.sh
    just _gen-il Expr test-projects/02-expr/Expr.kt
    just _gen-asm Expr dll
    cd test-projects/02-expr/csharp-test && dotnet run

# 03-hello: fun main() { println("Hello, .NET!") } — EXE с runtime.
test-03-hello: plugin runtime
    #!/usr/bin/env bash
    source scripts/activate.sh
    just _gen-il hello test-projects/03-hello/hello.kt
    just _gen-asm hello exe
    # Копируем runtime DLL рядом с EXE.
    cp runtime/bin/Release/net10.0/KotlinDotnetRuntime.dll build/
    # Генерируем runtimeconfig.json для framework-dependent запуска.
    printf '%s\n' \
      '{"runtimeOptions":{"tfm":"net10.0","framework":{"name":"Microsoft.NETCore.App","version":"10.0.11"},"rollForward":"Major"}}' \
      > build/hello.runtimeconfig.json
    dotnet build/hello.exe

# 04-loops: циклы while/do-while, break/continue, вызовы функций, интерполяция (Phase 9).
test-04-loops: plugin runtime
    #!/usr/bin/env bash
    source scripts/activate.sh
    just _gen-il Loops test-projects/04-loops/Loops.kt
    just _gen-asm Loops exe
    cp runtime/bin/Release/net10.0/KotlinDotnetRuntime.dll build/
    printf '%s\n' \
      '{"runtimeOptions":{"tfm":"net10.0","framework":{"name":"Microsoft.NETCore.App","version":"10.0.11"},"rollForward":"Major"}}' \
      > build/Loops.runtimeconfig.json
    out="$(dotnet build/Loops.exe)"
    expected="$(printf '10\n10\n5\n25\n42\nx = 42')"
    if [ "$out" != "$expected" ]; then
        echo "FAIL: 04-loops"
        echo "--- expected ---"
        echo "$expected"
        echo "--- got ---"
        echo "$out"
        exit 1
    fi
    echo ">>> 04-loops OK"

# 04-loops-spec: 4 spec-теста while/do-while (адаптировано из kotlin/tests-spec).
test-04-loops-spec: plugin runtime
    #!/usr/bin/env bash
    source scripts/activate.sh
    cp runtime/bin/Release/net10.0/KotlinDotnetRuntime.dll build/
    rcfg() {
        printf '%s\n' \
          '{"runtimeOptions":{"tfm":"net10.0","framework":{"name":"Microsoft.NETCore.App","version":"10.0.11"},"rollForward":"Major"}}' \
          > "build/$1.runtimeconfig.json"
    }
    for kt in test-projects/04-loops/spec/*.kt; do
        name="$(basename "$kt" .kt)"
        just _gen-il "$name" "$kt"
        just _gen-asm "$name" exe
        rcfg "$name"
        out="$(dotnet "build/$name.exe")"
        if [ "$out" != "OK" ]; then
            echo "FAIL: spec $name (got: $out)"
            exit 1
        fi
        echo ">>> spec $name OK"
    done

# Запустить все тесты.
test-all: test-00-int-add test-02-expr test-03-hello test-04-loops
    @echo ">>> all tests OK"

# === Отладка ===

# Показать сгенерированный IL-текст (последний — Arithmetic.il).
show-il:
    cat build/Arithmetic.il

# Дизассемблировать Arithmetic.dll через dotnet-ildasm.
disasm:
    #!/usr/bin/env bash
    source scripts/activate.sh
    dotnet-ildasm build/Arithmetic.dll

# Показать IR-дамп из плагина (последний).
show-ir:
    @cat build/ir-dump-*.txt 2>/dev/null || echo "no ir-dump found"

# === Очистка ===

# Удалить сборочные артефакты (build/ в корне и compiler-plugin/build/).
clean:
    rm -rf build/ compiler-plugin/build/

# Удалить локальные SDK (ОСТОРОЖНО: потребует повторный bootstrap).
clean-sdk:
    @echo ">>> Removing .sdk/ (run 'just bootstrap' to reinstall)"
    rm -rf .sdk/

# Удалить локальные исходники.
clean-sources:
    @echo ">>> Removing .sources/ (run 'just sources' to reinstall)"
    rm -rf .sources/
