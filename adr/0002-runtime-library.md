# ADR 0002: Runtime-библиотека KotlinDotnetRuntime

- **Дата:** 2026-08-17
- **Статус:** Accepted (Phase 8 — реализована минимально)

## Контекст

Kotlin stdlib (print, readLine, listOf, map, filter, строки и т.д.) не
маппится 1:1 на .NET BCL. Нужно промежуточное звено — аналогичное
`kotlin-stdlib-js` для JS-таргета.

## Решение

Создать `KotlinDotnetRuntime` — .NET class library (C#), на которую
ссылается сгенерированная сборка. IL-генератор эмитит вызовы вида
`[KotlinDotnetRuntime]Kotlin.Runtime.Print::println(object)`, а не
`[mscorlib]System.Console::WriteLine(string)`.

## Структура (текущая — Phase 8)

- `runtime/KotlinDotnetRuntime.csproj` (net10.0, library)
- `runtime/src/Print.cs` — `Kotlin.Runtime.Print`:
  - `println()`, `println(object?)`, `println(string)`, `println(int)`,
    `println(long)`, `println(double)`, `println(float)`, `println(bool)`,
    `println(char)`
  - `print(...)` — аналогично

## Структура (план — Phase 9+)

- `Kotlin.Runtime.Stdio` — readLine
- `Kotlin.Runtime.Strings` — строковые операции Kotlin-семантики
- `Kotlin.Runtime.Collections` — listOf, mapOf, map, filter, ...
- Заглушки для нереализованного — `throw NotImplementedException`

## Роль в pipeline

- Компилятор генерирует вызовы к runtime через `StdlibResolver.kt`.
- `StdlibResolver` маппит `kotlin.io.println` → `Kotlin.Runtime.Print::println`.
- IL-генератор эмитит `.assembly extern KotlinDotnetRuntime` всегда
  (упрощение PoC — двухпроходный обход для точного определения не нужен).
- `KotlinDotnetRuntime.dll` должна быть рядом со сгенерированным EXE
  (или в GAC / по пути разрешения сборок).

## Сборка

```bash
just runtime    # dotnet build runtime/ -c Release
```

Артефакт: `runtime/bin/Release/net10.0/KotlinDotnetRuntime.dll`.
