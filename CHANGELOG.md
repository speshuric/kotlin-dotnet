# Changelog

Все заметные изменения фиксируются здесь. Формат основан на [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added — `kotlinc-net.sh` CLI компилятор

- `scripts/kotlinc-net.sh` — единая точка компиляции `.kt` → `.exe`/`.dll`:
  ```
  ./scripts/kotlinc-net.sh <file.kt>              # → build/<name>.exe
  ./scripts/kotlinc-net.sh <file.kt> -dll         # → build/<name>.dll
  ./scripts/kotlinc-net.sh <file.kt> -o out.exe   # явное имя
  ./scripts/kotlinc-net.sh <file.kt> --rebuild-plugin
  ```
  Копирует `KotlinDotnetRuntime.dll` и генерирует `runtimeconfig.json` для EXE.
- `just compile <file.kt>` — обёртка над `kotlinc-net.sh`.
- Демо-секция в README.

### Added — Phase 8: Runtime + hello world (EXE)

- `runtime/` — KotlinDotnetRuntime (C# class library, net10.0):
  - `Print.cs` — `Kotlin.Runtime.Print.println/print` для всех примитивов.
- `StdlibResolver.kt` — маппинг `kotlin.io.println`/`print` → runtime-вызовы.
- `main()` → EXE с `.entrypoint` (раньше только DLL).
- `test-projects/03-hello/` — `fun main() { println("Hello, .NET!") }` →
  `hello.exe` печатает "Hello, .NET!".
- `just runtime` — собирать `KotlinDotnetRuntime.dll`.
- `just test-03-hello` — полный pipeline hello world (EXE + runtimeconfig.json).
- `just test-all` — все тесты (00-int-add, 02-expr, 03-hello).

### Added — Phase 7: Расширение выражений

- `test-projects/02-expr/` — арифметика, локальные переменные, if/when,
  сравнения, bool (&&/||/!), Long, Double, String-eq.
- `IlEmitter`: locals (`.locals init`), метки (`IL_N:`), `declareLocal`.
- `DotnetIrVisitor`: `IrVariable`, `IrSetValue`, `IrWhen`/`IrBranch`
  (if/when/ANDAND/OROR), `IrBlock`, все `IrConstKind` (Boolean/Long/Double/
  String/Char/Float), сравнения (GT/LT/GE/LE/EQEQ), унарные (neg/not),
  логические (and/or/xor), сдвиги (shl/shr/ushr).
- `just test-02-expr`, `just test-all`.
- `gradle.properties` — `org.gradle.java.installations.paths=.sdk/jdk`.

### Changed

- **`just` как единая точка входа для сборки** (ADR 0006).
  `justfile` в корне: `just bootstrap`, `just test`, `just list`,
  `just clean`, etc. mtime-инкрементальность на `il`/`dll` шагах.
  Удалены дубликаты `scripts/bootstrap.sh` и `scripts/build-test-add.sh`.
- Gradle 8.14.3 → **9.7.0** (bleeding edge, последний стабильный GA).
  Warning про deprecated 8.x исчез. KGP 2.4.20-RC поддерживает Gradle 9.x
  без верхней границы (`GradleCompatibilityCheck.kt`).

### Added — Phase 0–6: минимальный pipeline `test_add`

- Скелет проекта: `AGENTS.md`, `CLAUDE.md -> AGENTS.md`, `.gitignore`.
- `scripts/` для активации локального окружения (`activate.sh`, `deactivate.sh`,
  `install-sdks.sh`, `install-sources.sh`, `bootstrap.sh`, `build-test-add.sh`).
- Локальные SDK (всё в `.sdk/`, gitignored):
  - JDK Temurin 21
  - kotlinc 2.4.20-RC
  - .NET 10.0.400 SDK + global tools (`ilasm-cli`, `dotnet-ildasm`)
  - Gradle 8.14.3
- Локальные исходники (в `.sources/`, gitignored):
  - JetBrains/kotlin @ v2.4.20-RC (shallow clone)
  - dotnet/runtime @ release/10.0 (shallow clone)
- Compiler plugin (Kotlin 2.4.20-RC, K2):
  - `DotnetCompilerPluginRegistrar` — регистрация через `CompilerPluginRegistrar`
    (не устаревший `ComponentRegistrar`).
  - `DotnetIrGenerationExtension` — перехват IR через `IrGenerationExtension`.
  - `DotnetIrVisitor` — обход IR-дерева (`IrFile → IrSimpleFunction →
    IrBlockBody → IrReturn → IrCall(PLUS) → IrGetValue`).
  - `IlEmitter` — билдер IL-текста (assembly header, container class, static method).
  - `TypeMapper` — маппинг примитивных типов Kotlin → CIL.
  - `NameMapper` — маппинг package → namespace, file → `<File>Kt`, method verbatim.
- Тестовый проект `test-projects/00-int-add/`:
  - `Arithmetic.kt` — `package org.example.math; public fun test_add(a: Int, b: Int) = a + b`
  - `manual.il` — hand-written CIL (для отладки `ilasm` без компилятора).
  - `csharp-test/` — C# consumer (`<Reference HintPath>` на сгенерированную DLL).
- End-to-end pipeline работает:
  `Arithmetic.kt` → компилятор-плагин → `Arithmetic.il` → `ilasm` →
  `Arithmetic.dll` → C# consumer печатает `5`.

### Documentation

- ADRs:
  - `0001-pipeline-ir-to-il.md` — выбор IR → IL-текст → ilasm.
  - `0002-runtime-library.md` — роль KotlinDotnetRuntime (placeholder).
  - `0003-ir-state-at-interception.md` — состояние IR на перехвате (сценарий A).
  - `0004-net10-kotlin24-versions.md` — фиксация версий.
  - `0005-name-mapping.md` — мэппинг имён Kotlin ↔ .NET.
