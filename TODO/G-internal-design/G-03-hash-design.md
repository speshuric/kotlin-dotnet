# G-03: Дизайн — hash-коллизии и районы, где напоремся

- **Тема:** G. Internal design
- **Разметка:** POST-9 (дизайн)
- **Зависимости:** — (но связано с Phase 13 data-classes)
- **Статус:** TODO (design doc)

## Контекст

Пользователь: «подумать где мы напоремся с разным хешем».

«Разный хеш» — это:
1. **`hashCode()`/`equals()` семантика.** Kotlin `data class`
   генерирует `equals`/`hashCode` на основе полей. На .NET это
   `Equals`/`GetHashCode`. Нюансы:
   - Kotlin `String.hashCode()` ≠ .NET `String.GetHashCode()` (разные
     алгоритмы: Kotlin/Java — полиномиальный 31x; .NET — свой).
   - Kotlin `Int.hashCode() = theInt`; .NET `Int32.GetHashCode() = theInt` (ок).
   - `List<T>.hashCode()` — в Kotlin на основе элементов; .NET
     `List<T>.GetHashCode()` — reference-based (default `Object.GetHashCode`).
   - `Map`/`Set` — reference-based в .NET, value-based в Kotlin.
2. **`HashMap`/`HashSet`** — Kotlin использует `kotlin.collections.HashMap`
   (на JVM — `java.util.HashMap`); на .NET `Dictionary<K,V>` /
   `HashSet<T>` — другая реализация, другой hash-combine.
3. **Использование Kotlin-объектов как ключей в .NET-словарях**
   (или наоборот) — если `hashCode` различается, ключ «теряется».
4. **`null` ключ** — Kotlin `HashMap` позволяет null key; .NET
   `Dictionary<K,V>` — `KeyNotFoundException` для null? (на самом деле
   `Dictionary<,>` поддерживает null key для reference type, но
   не для value type).

## Цель

Design doc: где могут быть расхождения hash/equals, как с ними
бороться (через runtime? через codegen? через ADR?).

**Не** реализовывать — только зафиксировать риски.

## Задачи

### 1. Исследование

- Сравнить алгоритмы:
  - Kotlin `String.hashCode()` (из kotlin-stdlib, на JVM —
    `java.lang.String.hashCode`).
  - .NET `String.GetHashCode()` (на .NET — рандомизированный, не
    детерминированный между запусками для безопасности; но
    внутри процесса стабилен).
- Сравнить `List`/`Map`/`Set` hashCode (Kotlin value-based vs
  .NET reference-based).
- Проверить, что делает Kotlin IR для `data class` (генерирует
  `equals`/`hashCode` в теле, или полагается на stdlib).

### 2. Районы риска (проект)

| Где | Риск | Решение (проект) |
|---|---|---|
| `String.hashCode()` | Kotlin≠.NET | runtime-метод `Kotlin.Runtime.Strings.hashCode(string)` (Kotlin-алгоритм). Использовать, когда hashCode нужен Kotlin-семантически (data class с String-полем). |
| `data class hashCode` | поля с разной hash-семантикой | codegen: `hashCode()` = `Kotlin.Runtime.Objects.hash(field1, field2, ...)` (аналог `java.util.Objects.hash`), не `ValueTuple.GetHashCode`. |
| `data class equals` | поля List/Map — value-equals | codegen: `equals` = поэлементное сравнение через `Kotlin.Runtime.Objects.equals(a, b)` (глубокое). |
| `HashMap`/`HashSet` (Kotlin) | reference-based в .NET | runtime: `Kotlin.Runtime.Collections.HashMap` — обёртка над `Dictionary<K,V>` с Kotlin-`equals`/`hashCode` `IEqualityComparer<K>`. |
| `null` key | разное поведение | runtime: explicit-null-handling в `HashMap`. |
| Использование Kotlin-объекта как ключа в BCL-словаре | hash-несовпадение | ADR: не использовать BCL-словари напрямую для Kotlin-объектов; только через runtime. |

### 3. ADR / doc

Создать `docs/hash-design.md` + (опц.) ADR `0013-hash-semantics.md`:
- Риски (таблица).
- Стратегия: Kotlin-семантика через runtime (`Kotlin.Runtime.Objects`,
  `Kotlin.Runtime.Collections`), не через BCL напрямую.
- codegen для `data class`: использовать runtime-`equals`/`hash`,
  не `ValueTuple`/`Object.Equals`.
- `IEqualityComparer<T>` для runtime-collections.

## Приёмка

- `docs/hash-design.md` существует, описывает риски.
- В AGENTS.md §«Известные проблемы» — добавить ссылку на doc (или
  в `docs/` README).
- Реализации нет (POST-PoC / Phase 13).

## Заметки исполнителю

- Это **дизайн**, не реализация.
- Ключевой момент: `.NET String.GetHashCode()` — рандомизированный
  (для DoS-защиты), поэтому **нельзя** полагаться на него для
  персистентности/сериализации. Если Kotlin-код expects детерминированный
  string-hash — нужен runtime-метод с Kotlin-алгоритмом.
- Для PoC (базовые фазы) — неактуально; но зафиксировать, чтобы не
  напороться в Phase 13 (data classes, collections).
