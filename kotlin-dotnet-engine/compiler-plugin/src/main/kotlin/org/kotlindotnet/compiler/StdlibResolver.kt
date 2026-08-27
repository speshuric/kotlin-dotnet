package org.kotlindotnet.compiler

import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.kotlinFqName

/**
 * Facade over the stdlib redirect registry (see [StdlibMapping]).
 *
 * Resolution of a call: fully qualified Kotlin name -> registry entry ->
 * CALL operand built from actual argument types (overload selection at
 * the call site). The visitor keeps calling these two functions; nothing
 * else should talk to the registry directly.
 *
 * The known-stdlib check and the redirect lookup share one table, so
 * they can never disagree about what counts as covered.
 */
object StdlibResolver {

    /**
     * Does this call target a stdlib symbol present in the redirect
     * registry? Single point of truth for "we know how to emit this".
     */
    fun isStdlibCall(call: IrCall): Boolean {
        val fn = call.symbol.owner as? IrSimpleFunction ?: return false
        return StdlibMapping.lookup(fn.kotlinFqName.asString()) != null
    }

    /**
     * If [call] resolves to a registered runtime entry, returns the IL
     * signature operand:
     *   `void [KotlinDotnetRuntime]Kotlin.Runtime.Print::println(object)`
     * Returns null when the symbol is not in the registry.
     *
     * @param returnTypeCil CIL type of the call result (currently always
     *   void for redirected entries; kept for future non-void targets)
     * @param paramCilTypes CIL types of the evaluated arguments
     */
    fun resolveStdlibCall(
        call: IrCall,
        returnTypeCil: String,
        paramCilTypes: List<String>,
    ): String? {
        val fn = call.symbol.owner as? IrSimpleFunction ?: return null
        val entry = StdlibMapping.lookup(fn.kotlinFqName.asString()) ?: return null
        return StdlibMapping.callSignature(entry, paramCilTypes)
    }
}
