# kotlin-dotnet

Proof-of-concept компилятора Kotlin → .NET.

Pipeline: исходник Kotlin → K2 compiler plugin (`IrGenerationExtension`) →
обход IR-дерева → генерация CIL-текста (`.il`) → `ilasm` → .NET DLL/EXE.

Для управления сборкой и централизованного запуска команд проекта используется [just](https://github.com/casey/just). 

## Демо

```bash
# 1. Установить окружение (один раз).
just bootstrap                          # JDK 21, kotlinc, .NET 10, Gradle, ilasm, исходники
source scripts/activate.sh              # активировать env в текущем шелле (java/kotlinc/dotnet в PATH)
just plugin && just runtime             # собрать compiler-plugin JAR и KotlinDotnetRuntime.dll

# 2. Написать Kotlin.
cat > /tmp/demo.kt <<'EOF'
fun double(n: Int): Int = n * 2

fun main() {
    var x = 0
    while (x < 5) { x++ }
    println("x = $x")          // интерполяция
    println(double(21))        // вызов функции
}
EOF

# 3. Скомпилировать в .NET EXE.
#    (kotlinc-net.sh сам активирует env внутри, но для шага 4 нужен source выше)
./scripts/kotlinc-net.sh /tmp/demo.kt
# → build/demo.exe (+ KotlinDotnetRuntime.dll, runtimeconfig.json рядом)

# 4. Запустить.
dotnet build/demo.exe
# x = 5
# 42
```

## Статус

**Phase 9 завершена.** Pipeline работает end-to-end:
- `test_add(Int, Int): Int` → DLL → C# consumer печатает `5`.
- 16 выражений (арифметика, if/when, локальные переменные, bool, Long/Double) → DLL → C# проверки.
- `fun main() { println("Hello, .NET!") }` → EXE → печатает `Hello, .NET!`.
- циклы (`while`/`do-while`), `break`/`continue`, вызовы
  пользовательских top-level функций, строковая интерполяция (`"$x"`) → EXE
  с верификацией вывода. 4 spec-теста while/do-while (из `kotlin/tests-spec`)
  возвращают "OK". `for`-loop — TODO (требует итераторов).

## Быстрый старт

### Установка окружения (один раз)

```bash
just bootstrap      # JDK 21, kotlinc 2.4.20-RC, .NET 10, Gradle 9.7.0, ilasm, dotnet-ildasm
                    # + shallow clones исходников (JetBrains/kotlin, dotnet/runtime)
```

### Все тесты

```bash
just test-all       # 00-int-add, 02-expr, 03-hello, 04-loops
```

### Сборка `test_add` end-to-end

```bash
just test
```

Ожидаемый вывод:
```
[just] generating build/Arithmetic.il
...
[just] assembling build/Arithmetic.dll
...
5
```

Повторный `just test` пропустит пересборку (mtime-инкрементальность):
```
[just] build/Arithmetic.dll up-to-date (.il unchanged)
5
```

### Тесты циклов и функций (Phase 9)

```bash
just test-04-loops        # while/do-while/break/continue/call/concat → верификация вывода
just test-04-loops-spec   # 4 spec-теста while/do-while (из kotlin/tests-spec) → "OK"
```

### Пошагово

```bash
just list            # показать все рецепты
just plugin          # собрать compiler-plugin JAR (Gradle)
just runtime         # собрать KotlinDotnetRuntime.dll (C#, .NET 10)
just compile <f.kt>  # CLI: .kt → .exe/.dll (через kotlinc-net.sh)
just show-il         # показать сгенерированный IL
just disasm          # дизассемблировать Arithmetic.dll
just show-ir         # показать IR-дамп из плагина
just clean           # удалить build/
```

### Ручной IL (без компилятора Kotlin, для отладки ilasm)

```bash
source scripts/activate.sh
cd test-projects/00-int-add
ilasm /dll /output:manual.dll manual.il
dotnet-ildasm manual.dll | head -40
```

### Интерактивная работа

```bash
source scripts/activate.sh   # активировать env (java, kotlinc, dotnet, ilasm в PATH)
source scripts/deactivate.sh # снять env
```

## Структура

- `compiler-plugin/` — Kotlin compiler plugin (Gradle, K2 IR)
  - `src/main/kotlin/org/kotlindotnet/compiler/`
    - `DotnetCompilerPluginRegistrar.kt` — регистрация через `CompilerPluginRegistrar`
    - `DotnetCommandLineProcessor.kt` — CLI-опция `output.dir` (ADR 0007)
    - `DotnetIrGenerationExtension.kt` — точка входа `IrGenerationExtension`
    - `DotnetIrVisitor.kt` — обход IR-дерева → IL (Phase 0–9)
    - `TypeMapper.kt` — Kotlin → CIL маппинг примитивных типов
    - `NameMapper.kt` — package → namespace / `<File>Kt` / method (с экранированием CIL-опкод-имён)
    - `StdlibResolver.kt` — `println`/`print` → KotlinDotnetRuntime
    - `il/` — `IlEmitter` (интерфейс), `TextIlEmitter`, `IlOpcode`, `IlContext`
    - `ir/` — `IrOrigins`, `KotlinOperators`
    - `util/` — `Log`
  - `src/main/resources/META-INF/services/` — ServiceLoader-регистрация
- `runtime/` — KotlinDotnetRuntime (C#, .NET 10): `Print.cs` (println/print)
- `test-projects/`
  - `00-int-add/` — `test_add(Int,Int): Int` → DLL + C# consumer
  - `02-expr/` — 16 выражений → DLL + C# consumer
  - `03-hello/` — `fun main() { println(...) }` → EXE
  - `04-loops/` — циклы/функции/интерполяция → EXE + spec-тесты
- `scripts/` — окружение (вызывается из `justfile`)
  - `activate.sh` / `deactivate.sh` — активация локального окружения
  - `install-sdks.sh` — установка JDK/kotlinc/.NET/Gradle в `.sdk/`
  - `install-sources.sh` — shallow clones исходников в `.sources/`
  - `kotlinc-net.sh` — CLI: `.kt` → `.exe`/`.dll`
- `justfile` — единая точка входа (`just bootstrap`, `just test-all`, `just list`)
- `.sdk/` (gitignored) — локальные JDK, kotlinc, .NET, Gradle
- `.sources/` (gitignored, кроме README) — shallow clones JetBrains/kotlin + dotnet/runtime
- `adr/` — Architecture Decision Records (0001–0007)
- `docs/` — `il-reference.md`, `references.md`, `generics-strategy.md`
- `TODO/` — план выравнивающих задач + `PHASE-9.md`
- `AGENTS.md` — полное описание архитектуры, плана, принципов

См. [`AGENTS.md`](AGENTS.md) для полного описания архитектуры и плана.
