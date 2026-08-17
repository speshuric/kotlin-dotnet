# kotlin-dotnet

Proof-of-concept компилятора Kotlin → .NET.

Pipeline: исходник Kotlin → K2 compiler plugin (`IrGenerationExtension`) →
обход IR-дерева → генерация CIL-текста (`.il`) → `ilasm` → .NET DLL/EXE.

## Статус

**Phase 1–6 завершены.** Минимальный pipeline работает end-to-end:
Kotlin `test_add(Int, Int): Int` → `Arithmetic.dll` → C# consumer печатает `5`.

## Быстрый старт

### Установка окружения (один раз)

```bash
./scripts/bootstrap.sh        # JDK 21, kotlinc 2.4.20-RC, .NET 10, Gradle, ilasm, dotnet-ildasm
source scripts/activate.sh    # активировать env в текущем шелле
```

### Сборка `test_add` end-to-end

```bash
source scripts/activate.sh
./scripts/build-test-add.sh
```

Ожидаемый вывод:
```
...
=== Step 4: Run C# consumer (expects: 5) ===
5
=== Done. Pipeline succeeded. ===
```

### Пошагово

```bash
source scripts/activate.sh

# 1. Собрать плагин.
./gradlew :compiler-plugin:jar

# 2. Запустить kotlinc с плагином → build/Arithmetic.il
kotlinc -Xplugin=compiler-plugin/build/libs/dotnet-compiler-plugin-0.1.0-SNAPSHOT.jar \
        test-projects/00-int-add/Arithmetic.kt -d build/kt-out

# 3. ilasm → build/Arithmetic.dll
ilasm /dll /output:build/Arithmetic.dll build/Arithmetic.il

# 4. C# consumer
cd test-projects/00-int-add/csharp-test && dotnet run   # печатает 5
```

### Ручной IL (без компилятора Kotlin, для отладки ilasm)

```bash
source scripts/activate.sh
cd test-projects/00-int-add
ilasm /dll /output:manual.dll manual.il
dotnet-ildasm manual.dll | head -40
```

## Структура

- `compiler-plugin/` — Kotlin compiler plugin (Gradle, K2 IR)
  - `src/main/kotlin/org/kotlindotnet/compiler/`
    - `DotnetCompilerPluginRegistrar.kt` — регистрация через `CompilerPluginRegistrar`
    - `DotnetIrGenerationExtension.kt` — точка входа `IrGenerationExtension`
    - `DotnetIrVisitor.kt` — обход IR-дерева
    - `IlEmitter.kt` — билдер IL-текста
    - `TypeMapper.kt` — Kotlin → CIL маппинг примитивных типов
    - `NameMapper.kt` — Kotlin package → .NET namespace / class / method
  - `src/main/resources/META-INF/services/` — ServiceLoader-регистрация
- `runtime/` — KotlinDotnetRuntime (C#, появится в следующих фазах)
- `test-projects/00-int-add/` — минимальный тест
  - `Arithmetic.kt` — исходник Kotlin
  - `manual.il` — hand-written IL (для отладки ilasm без плагина)
  - `csharp-test/` — C#-consumer
- `scripts/` — окружение и сборка
  - `activate.sh` / `deactivate.sh` — активация локального окружения
  - `install-sdks.sh` — установка JDK/kotlinc/.NET/Gradle в `.sdk/`
  - `install-sources.sh` — shallow clones исходников в `.sources/`
  - `bootstrap.sh` — полный бутстрап + проверка
  - `build-test-add.sh` — полный конвейер сборки `test_add`
- `.sdk/` (gitignored) — локальные JDK, kotlinc, .NET, Gradle
- `.sources/` (gitignored, кроме README) — shallow clones JetBrains/kotlin + dotnet/runtime
- `adr/` — Architecture Decision Records
- `docs/` — документация

См. `AGENTS.md` для полного описания архитектуры и плана.
