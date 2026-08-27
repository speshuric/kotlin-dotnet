package org.kotlindotnet.compiler.ir

/**
 * Names of the Kotlin stdlib operator functions (`IrFunction.name.asString()`)
 * handled by [DotnetIrVisitor] (see A-05).
 *
 * The IR of operator calls (`a + b`, `!a`, ...) arrives as an `IrCall` on a
 * function named `plus`, `not`, etc. Comparing against these constants keeps
 * `when (symbolName)` typo-proof and readable.
 *
 * The list is not exhaustive: new operators are added as they appear.
 * An unknown operator is handled by `else -> TODO(...)` in the visitor.
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
    /** `a.inc()` / `a++` → `inc` (operator). For primitives → +1. */
    const val INC = "inc"
    /** `a.dec()` / `a--` → `dec` (operator). For primitives → -1. */
    const val DEC = "dec"

    /**
     * Every operator name handled by the visitor. Calls with other names are
     * treated as user-defined ones (top-level/instance, Phase 10).
     */
    val ALL: Set<String> = setOf(
        PLUS, MINUS, TIMES, DIV, REM,
        UNARY_MINUS, UNARY_PLUS, NOT,
        AND, OR, XOR, SHL, SHR, USHR,
        RANGE_TO, INC, DEC,
    )
}
