# ADR 0002: Runtime-библиотека KotlinDotnetRuntime

- **Дата:** 2026-08-17
- **Статус:** Proposed (на будущее — не реализуется в текущем PoC-цикле)

## Контекст

Kotlin stdlib (print, readLine, listOf, map, filter, строки и т.д.) не
маппится 1:1 на .NET BCL. Нужно промежуточное звено — аналогичное
`kotlin-stdlib-js` для JS-таргета.

## Решение

Создать `KotlinDotnetRuntime` — .NET class library (C#), на которую
ссылается сгенерированная сборка. IL-генератор эмитит вызовы вида
`[KotlinDotnetRuntime]Kotlin.Runtime.Print::println(object)`, а не
`[mscorlib]System.Console::WriteLine(string)`.

## Структура (план)

- `Kotlin.Runtime.Print` — println/print
- `Kotlin.Runtime.Stdio` — readLine
- `Kotlin.Runtime.Strings` — строковые операции Kotlin-семантики
- `Kotlin.Runtime.Collections` — listOf, mapOf, map, filter, ...
- Заглушки для нереализованного — `throw NotImplementedException`

## Роль в pipeline

- Компилятор генерирует простые вызовы к runtime.
- Семантика Kotlin живёт в runtime.
- Лёгкая замена/расширение: добавляем функции в runtime без изменения
  кодогенератора.
- Bootstrapping (этап 6): постепенно переписывать runtime с C# на Kotlin.

## Почему сейчас не реализуется

Текущий PoC-цикл (Phases 0–7) фокусируется на минимальном pipeline
`test_add(Int, Int): Int` — без печати, без stdlib-вызовов. Runtime
появится на этапе "hello world" (Phase 8+, AGENTS.md Этап 1).
