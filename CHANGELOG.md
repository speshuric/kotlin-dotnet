# Changelog

Все заметные изменения фиксируются здесь. Формат основан на [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

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
