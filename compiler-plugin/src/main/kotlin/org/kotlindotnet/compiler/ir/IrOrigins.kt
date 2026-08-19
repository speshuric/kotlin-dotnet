package org.kotlindotnet.compiler.ir

/**
 * Отладочные имена [org.jetbrains.kotlin.ir.IrElement.origin] для IR-узлов,
 * которые обрабатывает [DotnetIrVisitor] (см. A-05).
 *
 * `IrElement.origin?.debugName` возвращает строку — мы сравниваем её с
 * этими константами, чтобы `when(origin)` ловил опечатки на этапе
 * компиляции и был читаемым.
 *
 * Список не исчерпывающий: добавляем по мере появления новых узлов.
 * Неизвестный origin обрабатывается через `else -> TODO(...)` в visitor.
 *
 * TODO (B-01): когда появится карта обработчиков, можно перейти на
 * `enum class IrOrigin(val debugName: String)` и sealed-`when`.
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

    /** `a && b` (logical and; обычно приходит как `IrWhen`, см. [DotnetIrVisitor.emitAndAnd]). */
    const val ANDAND = "ANDAND"
    /** `a || b` (logical or; обычно приходит как `IrWhen`, см. [DotnetIrVisitor.emitOrOr]). */
    const val OROR = "OROR"
}
