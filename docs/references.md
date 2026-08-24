# Источники данных

## Kotlin-компилятор
- Репозиторий: https://github.com/JetBrains/kotlin
- IR-дерево: compiler/ir/ir.tree/
  - IrElement — базовый класс всех IR-элементов
  - IrTree.kt — спецификация, из которой генерируются классы
  - Ключевые элементы: IrModuleFragment, IrFile, IrClass,
    IrSimpleFunction, IrBlockBody, IrCall, IrConst, IrGetValue,
    IrReturn, IrWhen, IrBranch, IrTypeOperatorCall, и др.
- Существующие бэкенды (для референса):
  - compiler/ir/backend.jvm — JVM bytecode generation
  - compiler/ir/backend.js — JavaScript generation
  - compiler/ir/backend.wasm — WebAssembly generation
  - Kotlin/Native backend (в kotlin-native репозитории) — LLVM
- Сериализация IR:
  - compiler/ir/serialization.common — общий формат
  - compiler/ir/serialization.jvm, serialization.js, serialization.native
- FIR (Frontend IR): compiler/fir/
- IR README: compiler/ir/ReadMe.md

## Kotlin 2.0+ (K2 compiler)
- Документация: https://kotlinlang.org/docs/whatsnew20.html
- K2 стабилен с Kotlin 2.0, используется по умолчанию
- Архитектура: Frontend (FIR) → Backend (IR) → Codegen
- IR используется всеми бэкендами для lowering и оптимизации
- Compiler plugins поддерживаются: all-open, serialization,
  Compose, KSP, и др.

## .NET
- Стандарт формата сборок: ECMA-335
  https://www.ecma-international.org/publications-and-standards/standards/ecma-335/
- Описание формата:
  https://learn.microsoft.com/en-us/dotnet/standard/assembly-format
- .NET runtime: https://github.com/dotnet/runtime
- Roslyn (C# компилятор, референс по codegen):
  https://github.com/dotnet/roslyn
- ilasm — историческая утилита компиляции IL-текста (из пути удалена; см. ADR 0012)
  (входит в .NET SDK)

## Библиотеки для эмиссии .NET-сборок (для запасного варианта)
- dnlib: https://github.com/dnlib/dnlib (C# библиотека,
  создание/чтение/модификация .NET-сборок)
- System.Reflection.Metadata (встроенный .NET API)
