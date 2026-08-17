# ADR 0005: Мэппинг имён Kotlin ↔ .NET

- **Дата:** 2026-08-17
- **Статус:** Proposed

## Контекст

Kotlin и .NET имеют разные конвенции именования:
- Kotlin: lowercase пакеты, camelCase функции, PascalCase классы.
- .NET: PascalCase namespaces, PascalCase классы/методы.

Нужно зафиксировать мэппинг для IL-генератора.

## Решение (PoC)

| Измерение | Мэппинг | Пример |
|---|---|---|
| Kotlin package → .NET namespace | **verbatim** (lowercase, 1:1) | `org.example.math` → `org.example.math` |
| Контейнер top-level функций | **`<FileName>Kt`** (JVM-mirror) | `Arithmetic.kt` → класс `ArithmeticKt` |
| Имена методов | **verbatim** (включая `_`) | `test_add` → `test_add` |
| Default package (без `package`) | namespace пустой, `global::<File>Kt` | `global::ArithmeticKt` |
| Assembly/module name | из имени модуля/файла | `Arithmetic.dll` |

## Почему

- **Verbatim namespace** — минимум логики в кодогенераторе, ноль
  неожиданностей, легко дебажить IL. Обратный мэппинг (BCL → Kotlin)
  в PoC не нужен.
- **`<FileName>Kt`** — совпадает с JVM-бэкендом, проще сравнивать
  дампы IR/IL, нет коллизий с обычными классами.
- **Verbatim method** — CIL и C# допускают `_` в идентификаторах,
  прямой мэппинг без словаря.

## TODO (на будущее)

- **Namespace-трансформация:** заменить verbatim lowercase на
  PascalCase для идиоматичности .NET (`org.example.math` →
  `Org.Example.Math`). Потребуется обратный мэппинг для reflection
  и для вызовов BCL.
- **Mangled имена** для case-insensitive коллизий (Kotlin
  `foo` и `Foo` в одном пакете — в .NET одна таблица).
- **Property accessors** (`get`/`set` с `specialname`) — отдельная
  логика для property-синтаксиса.
- **Companion object** — статические методы на классе + вложенный
  класс (см. AGENTS.md).
