package org.kotlindotnet.compiler.ir

/**
 * Debug names of [org.jetbrains.kotlin.ir.IrElement.origin] for the IR nodes
 * handled by [DotnetIrVisitor] (see A-05).
 *
 * `IrElement.origin?.debugName` returns a string — we compare it against these
 * constants so that `when(origin)` catches typos at compile time and stays
 * readable.
 *
 * The list is not exhaustive: new nodes are added as they appear.
 * An unknown origin is handled by `else -> TODO(...)` in the visitor.
 *
 * TODO (B-01): once a handler map exists, we can switch to
 * an `enum class IrOrigin(val debugName: String)` and a sealed `when`.
 */
object IrOrigins {
    /** `a + b` (operator plus, binary). */
    const val PLUS = "PLUS"
    /** `a - b` (operator minus, binary). */
    const val MINUS = "MINUS"
    /** `a * b` (operator times, binary). */
    const val TIMES = "TIMES"
    /** `a / b` (operator div, binary). */
    const val DIV = "DIV"
    /** `a % b` (operator rem, binary). */
    const val PERC = "PERC"

    /** `a > b` (operator comparison, greater-than). */
    const val GT = "GT"
    /** `a < b` (operator comparison, less-than). */
    const val LT = "LT"
    /** `a >= b` (operator comparison, greater-or-equal). */
    const val GE = "GE"
    /** `a <= b` (operator comparison, less-or-equal). */
    const val LE = "LE"

    /** `a == b` (operator equals, nullable-aware). */
    const val EQEQ = "EQEQ"
    /** `a === b` (referential equality). */
    const val EQEQEQ = "EQEQEQ"

    /** `a && b` (logical and; usually arrives as an `IrWhen`, see [DotnetIrVisitor.emitAndAnd]). */
    const val ANDAND = "ANDAND"
    /** `a || b` (logical or; usually arrives as an `IrWhen`, see [DotnetIrVisitor.emitOrOr]). */
    const val OROR = "OROR"
}
