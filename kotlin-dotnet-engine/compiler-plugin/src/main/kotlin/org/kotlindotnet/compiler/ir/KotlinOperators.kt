package org.kotlindotnet.compiler.ir

/**
 * Имена operator-функций Kotlin stdlib (`IrFunction.name.asString()`),
 * которые обрабатывает [DotnetIrVisitor] (см. A-05).
 *
 * IR операторных вызовов (`a + b`, `!a`, ...) приходит как `IrCall` на
 * функцию с именем `plus`, `not` и т.д. Чтобы `when (symbolName)` ловил
 * опечатки и был читаем, сравниваем с этими константами.
 *
 * Список не исчерпывающий: добавляем по мере появления новых операторов.
 * Неизвестный оператор обрабатывается через `else -> TODO(...)` в visitor.
 */
object KotlinOperators {
    /** `a + b` → `plus`. */
    const val PLUS = "plus"
    /** `a - b` → `minus`. */
    const val MINUS = "minus"
    /** `a * b` → `times`. */
    const val TIMES = "times"
    /** `a / b` → `div`. */
    const val DIV = "div"
    /** `a % b` → `rem`. */
    const val REM = "rem"
    /** `-a` → `unaryMinus`. */
    const val UNARY_MINUS = "unaryMinus"
    /** `+a` → `unaryPlus`. */
    const val UNARY_PLUS = "unaryPlus"
    /** `!a` → `not`. */
    const val NOT = "not"
    /** `a and b` → `and`. */
    const val AND = "and"
    /** `a or b` → `or`. */
    const val OR = "or"
    /** `a xor b` → `xor`. */
    const val XOR = "xor"
    /** `a shl b` → `shl`. */
    const val SHL = "shl"
    /** `a shr b` → `shr`. */
    const val SHR = "shr"
    /** `a ushr b` → `ushr`. */
    const val USHR = "ushr"
    /** `a..b` → `rangeTo` (Phase 9). */
    const val RANGE_TO = "rangeTo"
    /** `a.inc()` / `a++` → `inc` (operator). Для примитивов → +1. */
    const val INC = "inc"
    /** `a.dec()` / `a--` → `dec` (operator). Для примитивов → -1. */
    const val DEC = "dec"

    /**
     * Все operator-имена, обрабатываемые visitor'ом. Вызовы с другими
     * именами считаются пользовательскими (top-level/instance, Phase 10).
     */
    val ALL: Set<String> = setOf(
        PLUS, MINUS, TIMES, DIV, REM,
        UNARY_MINUS, UNARY_PLUS, NOT,
        AND, OR, XOR, SHL, SHR, USHR,
        RANGE_TO, INC, DEC,
    )
}
