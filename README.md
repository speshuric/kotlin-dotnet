# kotlin-dotnet

Proof-of-concept компилятора Kotlin → .NET.

Pipeline: исходник Kotlin → K2 compiler plugin (`IrGenerationExtension`) →
обход IR-дерева → генерация метаданных + IL через dotnetutils (порт
System.Reflection.Metadata) → .NET DLL/EXE напрямую из JVM.

Для управления сборкой и централизованного запуска команд проекта используется [just](https://github.com/casey/just). 

## Демо

```bash
# 1. Установить окружение (один раз).
just bootstrap                          # JDK 21, kotlinc, .NET 10, Gradle, исходники
source scripts/activate.sh              # активировать env в текущем шелле (java/kotlinc/dotnet в PATH)
just build                              # собрать всё: plugin + runtime + тесты

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
# → build/demo/demo.exe (+ KotlinDotnetRuntime.dll, runtimeconfig.json рядом)

# 4. Запустить.
dotnet build/demo/demo.exe
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
just bootstrap      # JDK 21, kotlinc 2.4.20-RC, .NET 10, Gradle 9.7.0, dotnet-ildasm
                    # + shallow clones исходников (JetBrains/kotlin, dotnet/runtime)
```

### Все тесты

```bash
just test           # все тесты (00-int-add, 02-expr, 03-hello, 04-loops, 04-loops-spec)
just test 04-loops  # один тест по id
just test 04*       # по glob-маске
just test-smoke     # == just test-short == just test 00-int-add (дымовой)
```

### Сборка целиком

```bash
just build          # plugin + runtime + все тесты (config=debug по умолчанию)
just build release  # то же в Release-конфигурации runtime
just build-no-test  # собрать без верификации (IL + DLL/EXE + runtime-copy)
```

### Сборка `test_add` end-to-end

```bash
just test 00-int-add
```

Ожидаемый вывод:
```
compiling test-projects/00-int-add/Arithmetic.kt → build/00-int-add/Arithmetic.dll
...
>>> 00-int-add OK
```

### Тесты циклов и функций (Phase 9)

```bash
just test 04-loops        # while/do-while/break/continue/call/concat → верификация вывода
just test 04-loops-spec   # 4 spec-теста while/do-while (из kotlin/tests-spec) → "OK"
```

### Пошагово

```bash
just list             # показать все рецепты
just plugin           # собрать compiler-plugin JAR (Gradle)
just runtime          # собрать KotlinDotnetRuntime.dll (C#, .NET 10)
just compile <f.kt>   # CLI: .kt → .exe/.dll (через kotlinc-net.sh)
just disasm last      # дизассемблировать последнюю DLL (dotnet-ildasm)
just show-ir          # показать последний IR-дамп из плагина
just clean build      # удалить только сборочные артефакты (build/, runtime/bin)
just clean sdk        # удалить .sdk/ (требует пере-bootstrap)
just clean             # удалить всё — build + .sdk + .sources (требует пере-bootstrap!)
```

> **Структура `build/`:** артефакты изолированы по тестам — `build/<testid>/`
> (напр. `build/04-loops/Loops.exe`, `build/04-loops-spec/while_2_1/while_2_1.exe`).
> `disasm`/`show-ir` принимают selector: `last` (по умолчанию) |
> `all` | `<testid>` | `<glob>`.

### Интерактивная работа

```bash
source scripts/activate.sh   # активировать env (java, kotlinc, dotnet в PATH)
source scripts/deactivate.sh # снять env
```

## Структура

- `kotlin-dotnet-engine/` — Gradle-корень JDK-области (все Kotlin-проекты)
  - `compiler-plugin/` — Kotlin compiler plugin (K2 IR)
    - `src/main/kotlin/org/kotlindotnet/compiler/`
      - `DotnetCompilerPluginRegistrar.kt` — регистрация через `CompilerPluginRegistrar`
      - `DotnetCommandLineProcessor.kt` — CLI-опция `output.dir` (ADR 0007)
      - `DotnetIrGenerationExtension.kt` — точка входа `IrGenerationExtension`
      - `DotnetIrVisitor.kt` — обход IR-дерева → IL (Phase 0–10)
      - `TypeMapper.kt` — Kotlin → CIL маппинг примитивных типов
      - `NameMapper.kt` — package → namespace / `<File>Kt` / method name
      - `StdlibResolver.kt` — `println`/`print` → KotlinDotnetRuntime
      - `il/` — `IlEmitter` (интерфейс), `IlOpcode`
      - `ir/` — `IrOrigins`, `KotlinOperators`
      - `util/` — `Log`
    - `src/main/resources/META-INF/services/` — ServiceLoader-регистрация
  - `dotnetutils/` — порт write-path System.Reflection.Metadata → Kotlin
    (прямая генерация PE; чистый kotlin-stdlib; ADR 0009)
- `runtime/` — KotlinDotnetRuntime (C#, .NET 10): `Print.cs` (println/print)
- `test-projects/`
  - `00-int-add/` — `test_add(Int,Int): Int` → DLL + C# consumer
  - `02-expr/` — 16 выражений → DLL + C# consumer
  - `03-hello/` — `fun main() { println(...) }` → EXE
  - `04-loops/` — циклы/функции/интерполяция → EXE + spec-тесты
- `scripts/` — окружение и сборочные скрипты (вызывается из `justfile`, ADR 0008)
  - `activate.sh` / `deactivate.sh` — активация локального окружения
  - `install-sdks.sh` — установка JDK/kotlinc/.NET/Gradle в `.sdk/`
  - `install-sources.sh` — shallow clones исходников в `.sources/`
  - `common.sh` — общие функции (source-only)
  - `kotlinc-net.sh` — CLI: `.kt` → `.exe`/`.dll`
  - `tests.sh` — реестр тестов (source-only, id → .kt/kind/consumer)
  - `build-test.sh` — сборка + верификация одного теста
  - `build.sh` — диспетчер сборки (`plugin` | `runtime` | `all`)
  - `test.sh` — диспетчер тестов (selector → список id)
  - `show.sh` — `ir`/`disasm` × `last`/`all`/`<testid>`/`<glob>`
  - `clean.sh` — очистка (`all` | `build` | `sdk` | `sources`)
- `justfile` — единая точка входа: `just bootstrap`, `just build`, `just test`, `just list`
- `build/` (gitignored) — per-test артефакты: `build/<testid>/{*.dll,*.exe,ir-dump-*.txt,...}`
- `.sdk/` (gitignored) — локальные JDK, kotlinc, .NET, Gradle
- `.sources/` (gitignored, кроме README) — shallow clones JetBrains/kotlin + dotnet/runtime
- `adr/` — Architecture Decision Records (0001–0008)
- `docs/` — `plugin-options.md`, `il-reference.md`, `references.md`, `generics-strategy.md`
- `TODO/` — план выравнивающих задач + `PHASE-9.md`
- `AGENTS.md` — полное описание архитектуры, плана, принципов

См. [`AGENTS.md`](AGENTS.md) для полного описания архитектуры и плана.
