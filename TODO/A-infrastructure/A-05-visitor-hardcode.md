# A-05: DotnetIrVisitor — убрать хардкод (origin, stdlib)

- **Тема:** A. Infrastructure
- **Разметка:** PRE-9
- **Зависимости:** A-01 (Log), A-04 (IlOpcode enum)
- **Статус:** TODO

## Контекст

Файл: `compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/DotnetIrVisitor.kt`.

Строка 207 (указанная пользователем):
```kotlin
private fun emitCall(call: IrCall, data: Nothing?) {
    val origin = call.origin?.debugName
    when (origin) {
        "GT", "LT", "GE", "LE" -> { ... }
        "EQEQ", "EQEQEQ" -> { ... }
        "ANDAND", "OROR" -> { TODO(...) }
    }
    ...
    val calleeFqName = (callee as? IrSimpleFunction)?.kotlinFqName?.asString()
    if (calleeFqName == "kotlin.io.println" || calleeFqName == "kotlin.io.print") {
        emitStdlibCall(call, data)
        return
    }
    ...
    when (symbolName) {
        "plus" -> emitter.line("add")
        "minus" -> emitter.line("sub")
        ...
        "unaryPlus" -> Unit
        "not" -> { emitter.line("ldc.i4.0"); emitter.line("ceq") }
        "and" -> emitter.line("and")
        ...
        "rangeTo" -> TODO("rangeTo not supported in PoC (Phase 9)")
        else -> {
            if (origin == "PERC") emitter.line("rem")
            else TODO("Call to '$symbolName' (origin=$origin) not supported in PoC")
        }
    }
}
```

Хардкод:
1. **`origin.debugName` — строки** (`"GT"`, `"LT"`, `"EQEQ"`...). Это
   внутренние имена Kotlin IR-origins. Должны быть в enum/object, чтобы
   опечатки ловились и `when` был исчерпывающим.
2. **`kotlinFqName.asString()` — строки** (`"kotlin.io.println"`,
   `"kotlin.io.print"`). Это принадлежит `StdlibResolver` (см. D-02),
   не visitor.
3. **`symbolName` — строки** (`"plus"`, `"minus"`, `"times"`...).
   Имена operator-функций Kotlin stdlib. Должны быть в enum/object
   `KotlinOperator`.
4. **`"PERC"` — отдельный кейс** в `else`, что намекает на
   неструктурированное расширение.
5. `emitter.line(...)` — см. A-03/A-04 (миграция на `opcode`).

## Цель

Убрать из visitor все «магические строки»:
- origins → `object IrOrigins` / `enum class IrOrigin` (хотя бы для
  тех, что используются: PLUS, MINUS, TIMES, DIV, PERC, GT, LT, GE,
  LE, EQEQ, EQEQEQ, ANDAND, OROR, ...).
- operator names → `object KotlinOperators` (PLUS="plus", MINUS="minus",
  ...).
- stdlib fqNames → делегировать в `StdlibResolver` (D-02), не
  хардкодить в visitor.

## Задачи

1. Создать `compiler-plugin/src/main/kotlin/org/kotlindotnet/compiler/IrOrigins.kt`:
   ```kotlin
   object IrOrigins {
       const val PLUS = "PLUS"
       const val MINUS = "MINUS"
       const val TIMES = "TIMES"
       const val DIV = "DIV"
       const val PERC = "PERC"
       const val GT = "GT"
       const val LT = "LT"
       const val GE = "GE"
       const val LE = "LE"
       const val EQEQ = "EQEQ"
       const val EQEQEQ = "EQEQEQ"
       const val ANDAND = "ANDAND"
       const val OROR = "OROR"
       // ...
   }
   ```
   Или `enum class IrOrigin(val debugName: String)` — на усмотрение
   исполнителя; `object` проще для текущего `when(origin)`.
2. Создать `KotlinOperators.kt`:
   ```kotlin
   object KotlinOperators {
       const val PLUS = "plus"
       const val MINUS = "minus"
       const val TIMES = "times"
       const val DIV = "div"
       const val REM = "rem"
       const val UNARY_MINUS = "unaryMinus"
       const val UNARY_PLUS = "unaryPlus"
       const val NOT = "not"
       const val AND = "and"
       const val OR = "or"
       const val XOR = "xor"
       const val SHL = "shl"
       const val SHR = "shr"
       const val USHR = "ushr"
       const val RANGE_TO = "rangeTo"
   }
   ```
3. Мигрировать `emitCall`:
   - `when (origin)` → `when (origin)` с `IrOrigins.GT` и т.д.
   - `when (symbolName)` → с `KotlinOperators.PLUS` и т.д.
   - Убрать `else { if (origin == "PERC") ... }` — внести `PERC` в
     основной `when` (origin-based, а не symbolName-based; либо
     проверить, что PERC приходит через origin, а не symbolName).
4. Stdlib-хардкод (`calleeFqName == "kotlin.io.println" || ...`):
   - Заменить на `if (StdlibResolver.isStdlibCall(call)) { emitStdlibCall(...) ; return }`.
   - `isStdlibCall` — метод `StdlibResolver` (см. D-02). Сейчас можно
     вынести проверку туда, не меняя поведение.
5. Убедиться, что `when` по операторам/origins либо исчерпывающий,
   либо `else -> TODO(...)` с контекстом.
6. После A-04 мигрировать `emitter.line` → `emitter.opcode`.

## Приёмка

- `grep -n '"plus"\|"minus"\|"GT"\|"EQEQ"\|"kotlin.io.println"'
  compiler-plugin/.../DotnetIrVisitor.kt` → 0 совпадений (строки
  уехали в `IrOrigins`/`KotlinOperators`/`StdlibResolver`).
- `just test-all` зелёный, IL идентичен.

## Заметки исполнителю

- Это задача на «вынос хардкода в константы», не на смену логики.
  Поведение не меняется.
- Контекст: `DotnetIrVisitor.kt` + новые `IrOrigins.kt`,
  `KotlinOperators.kt`. `StdlibResolver.kt` — только если делаешь
  `isStdlibCall` (иначе оставить для D-02).
