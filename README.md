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
just bootstrap      # JDK 21, kotlinc 2.4.20-RC, .NET 10, Gradle 9.7.0, ilasm, dotnet-ildasm
                    # + shallow clones исходников (JetBrains/kotlin, dotnet/runtime)
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

### Пошагово

```bash
just list            # показать все рецепты
just plugin          # собрать compiler-plugin JAR (Gradle)
just il              # kotlinc + plugin → build/Arithmetic.il
just dll             # ilasm → build/Arithmetic.dll
just test            # C# consumer → печатает 5
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
- `scripts/` — окружение (вызывается из `justfile`)
  - `activate.sh` / `deactivate.sh` — активация локального окружения
  - `install-sdks.sh` — установка JDK/kotlinc/.NET/Gradle в `.sdk/`
  - `install-sources.sh` — shallow clones исходников в `.sources/`
- `justfile` — единая точка входа (`just bootstrap`, `just test`, `just list`)
- `.sdk/` (gitignored) — локальные JDK, kotlinc, .NET, Gradle
- `.sources/` (gitignored, кроме README) — shallow clones JetBrains/kotlin + dotnet/runtime
- `adr/` — Architecture Decision Records
- `docs/` — документация

См. `AGENTS.md` для полного описания архитектуры и плана.
