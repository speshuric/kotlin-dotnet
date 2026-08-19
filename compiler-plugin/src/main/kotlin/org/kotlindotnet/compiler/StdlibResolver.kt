package org.kotlindotnet.compiler

import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.kotlinFqName

/**
 * Маппинг вызовов kotlin stdlib → KotlinDotnetRuntime (см. ADR 0002).
 *
 * PoC: только `kotlin.io.println` и `kotlin.io.print`.
 */
object StdlibResolver {

    private const val RUNTIME_ASSEMBLY = "KotlinDotnetRuntime"
    private const val RUNTIME_NS = "Kotlin.Runtime"

    /**
     * Является ли [call] вызовом stdlib-функции, которую мы маппим на
     * KotlinDotnetRuntime (см. ADR 0002, A-05).
     *
     * Единая точка проверки: использовалась вместо дублирования
     * `fqName == "kotlin.io.println" || ...` в [DotnetIrVisitor].
     */
    fun isStdlibCall(call: IrCall): Boolean {
        val fn = call.symbol.owner as? IrSimpleFunction ?: return false
        val fqName = fn.kotlinFqName.asString()
        return fqName == "kotlin.io.println" || fqName == "kotlin.io.print"
    }

    /**
     * Если [call] — вызов stdlib-функции, которую мы маппим на runtime,
     * возвращает IL-сигнатуру вызова в формате:
     *   `void [KotlinDotnetRuntime]Kotlin.Runtime.Print::println(object)`
     *
     * Иначе — null (не stdlib-вызов).
     *
     * @param returnTypeCil CIL-тип возврата (для метода-вызова).
     * @param paramCilTypes CIL-типы аргументов (уже вычисленные).
     */
    fun resolveStdlibCall(
        call: IrCall,
        returnTypeCil: String,
        paramCilTypes: List<String>
    ): String? {
        val fn = call.symbol.owner as? IrSimpleFunction ?: return null
        val fqName = fn.kotlinFqName.asString()
        return when (fqName) {
            "kotlin.io.println" -> {
                val paramSig = if (paramCilTypes.isEmpty()) "" else paramCilTypes.joinToString(", ")
                "void [$RUNTIME_ASSEMBLY]$RUNTIME_NS.Print::println($paramSig)"
            }
            "kotlin.io.print" -> {
                val paramSig = if (paramCilTypes.isEmpty()) "" else paramCilTypes.joinToString(", ")
                "void [$RUNTIME_ASSEMBLY]$RUNTIME_NS.Print::print($paramSig)"
            }
            else -> null
        }
    }
}
