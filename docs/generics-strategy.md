# Дженерики: JVM type erasure vs .NET reified generics

> Вынесено из AGENTS.md для уменьшения контекста.
> Актуально для Phase 12+.

## Почему .NET reified generics — это преимущество, а не проблема

.NET сохраняет type parameters в runtime (reified), JVM стирает (erasure).
Это значит:
- `List<string>` и `List<int>` — **разные типы** в .NET runtime
- **Не нужны** bridge-методы для covariance/contravariance
- **Не нужны** явные cast'ы — type safety на уровне runtime
- `List<int>` — специализированный тип, **без boxing** для value types

## Variance: маппинг почти 1:1

Kotlin и .NET оба используют **declaration-site variance** (не Java wildcards):

| Kotlin                       | .NET (IL)                           |
|------------------------------|-------------------------------------|
| `class Box<T>`               | `class Box<T>` (invariant)          |
| `interface Producer<out T>`  | `interface Producer<+T>` (covariant)|
| `interface Consumer<in T>`   | `interface Consumer<-T>` (contravariant) |

## Критический вопрос: в каком состоянии IR до нас доходит?

Pipeline компилятора:
```
Frontend (FIR) → IR Generation → IR Lowering → Backend Codegen
                                      ↑
                          JVM-specific passes тут?
```

**Ответ (ADR 0003):** сценарий A — IR сохраняет полные type arguments.
Проверено для `test_add` (без дженериков). Полная проверка — Phase 12.

## Проблемные случаи и решения для PoC

1. **Star projection (`Array<*>`, `List<*>`)**:
   - В .NET нет прямого аналога.
   - PoC: маппить на `Array<object>` / `List<object>` с потерей type safety.
   - TODO: корректная обработка через variance-правила .NET.

2. **Use-site variance (`Box<out Number>`)**:
   - Редко, в основном для Java interop (неактуально).
   - PoC: игнорировать, маппить на `Box<object>` с TODO.

3. **`reified` type parameters**:
   - Работают через inline-инлайнинг — фронтенд разворачивает.
   - К нам в IR уже приходит развёрнутый код с конкретным типом.
   - **Не нужно ничего делать.**

4. **Type checks с дженериками (`x is List<String>`)**:
   - В JVM: невозможно из-за erasure (только `x is List<*>`).
   - В .NET: **возможно** через reflection.
   - PoC: если IR содержит generic type check — генерировать runtime check
     через .NET reflection. Если IR уже lowering'нул в erased check —
     всё равно работает, менее точно.

5. **Generic method instantiation**:
   - `fun <T> identity(x: T): T = x`
   - В .NET IL: метод с generic parameter (как в JVM bytecode).
   - Маппинг прямой: `.method <T> T identity(T x)`.

6. **Generic specialization для value types**:
   - JVM: `List<int>` невозможен → `List<Integer>` с boxing.
   - .NET: `List<int>` — специализированный тип, без boxing.
   - Если IR содержит `List<Int>` — генерируем `List<int32>`.
   - Если IR уже lowering'нул в erased `List<Object>` — работает,
     но с boxing. TODO: восстановить type arguments из IR, если возможно.
