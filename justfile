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
    echo ">>> Bootstrap complete. Run 'just test' to verify pipeline."

# === Сборка compiler plugin ===

# Собрать compiler-plugin JAR через Gradle (инкрементально — Gradle UP-TO-DATE).
plugin:
    #!/usr/bin/env bash
    source scripts/activate.sh
    ./gradlew :compiler-plugin:jar

# === Pipeline test_add ===

# Сгенерировать build/Arithmetic.il из Arithmetic.kt через kotlinc + plugin.
# Инкрементально: пропускает пересборку если .kt и plugin JAR не менялись.
il: plugin
    #!/usr/bin/env bash
    source scripts/activate.sh
    kt=test-projects/00-int-add/Arithmetic.kt
    jar=compiler-plugin/build/libs/dotnet-compiler-plugin-0.1.0-SNAPSHOT.jar
    out=build/Arithmetic.il
    if [ -f "$out" ] && [ "$out" -nt "$kt" ] && [ "$out" -nt "$jar" ]; then
        echo "[just] $out up-to-date (kt + plugin unchanged)"
        exit 0
    fi
    echo "[just] generating $out"
    mkdir -p build/kt-out
    rm -f build/ir-dump.txt "$out"
    kotlinc -Xplugin="$jar" "$kt" -d build/kt-out

# Собрать build/Arithmetic.dll через ilasm.
# Инкрементально: пропускает если .il не менялся.
dll: il
    #!/usr/bin/env bash
    source scripts/activate.sh
    il=build/Arithmetic.il
    out=build/Arithmetic.dll
    if [ -f "$out" ] && [ "$out" -nt "$il" ]; then
        echo "[just] $out up-to-date (.il unchanged)"
        exit 0
    fi
    echo "[just] assembling $out"
    ilasm /dll /output:"$out" "$il"

# Запустить C# consumer — должен напечатать 5.
test: dll
    #!/usr/bin/env bash
    source scripts/activate.sh
    cd test-projects/00-int-add/csharp-test
    dotnet run

# === Отладка ===

# Показать сгенерированный IL-текст.
show-il:
    cat build/Arithmetic.il

# Дизассемблировать Arithmetic.dll через dotnet-ildasm.
disasm:
    #!/usr/bin/env bash
    source scripts/activate.sh
    dotnet-ildasm build/Arithmetic.dll

# Показать IR-дамп из плагина.
show-ir:
    cat build/ir-dump.txt

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
