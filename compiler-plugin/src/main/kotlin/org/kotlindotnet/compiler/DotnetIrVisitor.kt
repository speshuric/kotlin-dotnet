package org.kotlindotnet.compiler

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrVisitor

/**
 * Посетитель IR-дерева, эмитящий IL-текст.
 *
 * PoC Phase 7: покрывает:
 * - `IrFile → IrSimpleFunction(top-level) → IrBlockBody`
 * - `IrReturn`, `IrCall` (operator + сравнения), `IrGetValue`, `IrConst`
 * - `IrVariable` (локальные переменные, `val`/`var`)
 * - `IrSetValue` (присваивание локалке)
 * - `IrWhen` + `IrBranch` (if/when/ANDAND/OROR)
 * - `IrBlock` (блок выражений)
 *
 * Возвращает Unit: IlEmitter накапливает состояние внутри себя.
 *
 * Контекст вызова отслеживается через [MethodContext] (стек методов).
 */
class DotnetIrVisitor(
    private val emitter: IlEmitter
) : IrVisitor<Unit, Nothing?>() {

    /** Индексы локальных переменных по их символу (в текущем методе). */
    private val variableIndices = mutableMapOf<IrVariable, Int>()

    override fun visitElement(element: IrElement, data: Nothing?) {
        TODO("IR element not supported in PoC: ${element::class.simpleName}")
    }

    override fun visitFile(declaration: IrFile, data: Nothing?) {
        val ns = NameMapper.namespace(declaration)
        val cls = NameMapper.containerClass(declaration)
        val asm = NameMapper.assemblyName(declaration)

        // Эмитим extern KotlinDotnetRuntime всегда — лишний extern не вредит,
        // а двухпроходный обход для поиска stdlib-вызовов усложнит PoC.
        emitter.assemblyHeader(asm, withRuntime = true)
        emitter.beginContainerClass(ns, cls)
        for (decl in declaration.declarations) {
            if (decl !is IrSimpleFunction) continue
            emitSimpleFunction(decl, data)
        }
        emitter.endContainerClass()
    }

    private fun emitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?) {
        if (declaration.dispatchReceiverParameter != null) {
            TODO("Instance functions not supported in PoC")
        }

        // Сброс состояния для нового метода.
        variableIndices.clear()
        emitter.resetMethodState()

        val name = NameMapper.methodName(declaration)
        val retType = TypeMapper.mapType(declaration.returnType)
        val params = declaration.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .map { p: IrValueParameter ->
                TypeMapper.mapType(p.type) to p.name.asString()
            }

        // main() → .entrypoint (Phase 8).
        val isEntrypoint = name == "main"
        emitter.beginStaticMethod(name, retType, params, isEntrypoint = isEntrypoint)

        // Предварительно обходим тело, чтобы собрать локальные переменные.
        // Это нужно, чтобы .locals init шёл до инструкций.
        val body = declaration.body
            ?: error("Function $name has no body")
        collectLocals(body)
        emitter.emitLocalsIfAny()

        emitBody(body, data)

        // Для void-методов без явного ret — добавляем.
        if (retType == "void") {
            emitter.line("ret")
        }

        emitter.endStaticMethod()
    }

    /** Предварительный обход для регистрации всех IrVariable в .locals. */
    private fun collectLocals(element: IrElement) {
        when (element) {
            is IrVariable -> {
                val cilType = TypeMapper.mapType(element.type)
                val idx = emitter.declareLocal(cilType)
                variableIndices[element] = idx
                element.initializer?.let { collectLocals(it) }
            }
            is IrBlockBody -> element.statements.forEach { collectLocals(it) }
            is IrBlock -> element.statements.forEach { collectLocals(it) }
            is IrWhen -> {
                element.branches.forEach { b ->
                    collectLocals(b.condition)
                    collectLocals(b.result)
                }
            }
            is IrCall -> element.arguments.forEach { a -> a?.let { collectLocals(it) } }
            is IrReturn -> collectLocals(element.value)
            is IrGetValue -> Unit
            is IrConst -> Unit
            is IrSetValue -> collectLocals(element.value)
            else -> Unit
        }
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?) {
        emitSimpleFunction(declaration, data)
    }

    private fun emitBody(body: IrElement, data: Nothing?) {
        when (body) {
            is IrBlockBody -> {
                for (stmt in body.statements) {
                    emitStatement(stmt, data)
                }
            }
            else -> emitStatement(body, data)
        }
    }

    private fun emitStatement(e: IrElement, data: Nothing?) {
        when (e) {
            is IrReturn -> emitReturn(e, data)
            is IrVariable -> emitVariable(e, data)
            is IrSetValue -> emitSetValue(e, data)
            is IrCall -> emitExpr(e, data)
            is IrGetValue -> emitExpr(e, data)
            is IrConst -> emitExpr(e, data)
            is IrWhen -> emitWhenAsStatement(e, data)
            is IrBlock -> {
                for (stmt in e.statements) emitStatement(stmt, data)
            }
            else -> e.accept(this, data)
        }
    }

    private fun emitExpr(e: IrExpression, data: Nothing?) {
        when (e) {
            is IrCall -> emitCall(e, data)
            is IrGetValue -> emitGetValue(e)
            is IrConst -> emitConst(e)
            is IrWhen -> emitWhenAsExpression(e, data)
            is IrBlock -> {
                for (stmt in e.statements) emitStatement(stmt, data)
            }
            is IrSetValue -> emitSetValue(e, data)
            else -> e.accept(this, data)
        }
    }

    private fun emitReturn(ret: IrReturn, data: Nothing?) {
        if (ret.value.type.isUnit()) {
            emitter.line("ret")
        } else {
            emitExpr(ret.value, data)
            emitter.line("ret")
        }
    }

    private fun emitVariable(v: IrVariable, data: Nothing?) {
        val idx = variableIndices[v]
            ?: error("Variable ${v.name} not registered in .locals")
        v.initializer?.let { init ->
            emitExpr(init, data)
            emitter.line("stloc.$idx")
        }
    }

    private fun emitSetValue(s: IrSetValue, data: Nothing?) {
        emitExpr(s.value, data)
        val owner = s.symbol.owner
        when (owner) {
            is IrVariable -> {
                val idx = variableIndices[owner]
                    ?: error("Variable ${owner.name} not registered in .locals")
                emitter.line("stloc.$idx")
            }
            else -> TODO("SetValue of ${owner::class.simpleName} not supported in PoC")
        }
    }

    private fun emitCall(call: IrCall, data: Nothing?) {
        val origin = call.origin?.debugName

        // Сначала обрабатываем special origins (сравнения, логические),
        // т.к. у них своя IL-семантика.
        when (origin) {
            "GT", "LT", "GE", "LE" -> {
                emitBinaryArgs(call, data)
                emitComparison(origin)
                return
            }
            "EQEQ", "EQEQEQ" -> {
                emitBinaryArgs(call, data)
                emitter.line("ceq")
                return
            }
            "ANDAND", "OROR" -> {
                // На самом деле && / || в Kotlin lowering превращается в IrWhen
                // (см. дамп test_bool). Если всё же пришли как IrCall — fallback.
                TODO("ANDAND/OROR expected to be IrWhen, got IrCall")
            }
        }

        // Stdlib-вызовы (println, print) → KotlinDotnetRuntime.
        val callee = call.symbol.owner
        val calleeFqName = (callee as? IrSimpleFunction)?.kotlinFqName?.asString()
        if (calleeFqName == "kotlin.io.println" || calleeFqName == "kotlin.io.print") {
            emitStdlibCall(call, data)
            return
        }

        // Обычный operator-call: эмитим аргументы, затем opcode.
        emitCallArguments(call, data)

        // Унарные / бинарные операторы.
        val symbolName = call.symbol.owner.name.asString()
        when (symbolName) {
            "plus" -> emitter.line("add")
            "minus" -> emitter.line("sub")
            "times" -> emitter.line("mul")
            "div" -> emitter.line("div")
            "rem" -> emitter.line("rem")
            "unaryMinus" -> emitter.line("neg")
            "unaryPlus" -> Unit // nop
            "not" -> {
                // Для Boolean: not это логическое НЕ, не побитовое.
                // not(true)=1 → 0xFFFFFFFE (побитово), а нужно 0.
                // Используем ldc.i4.0 + ceq (logical NOT).
                emitter.line("ldc.i4.0")
                emitter.line("ceq")
            }
            "and" -> emitter.line("and")
            "or" -> emitter.line("or")
            "xor" -> emitter.line("xor")
            "shl" -> emitter.line("shl")
            "shr" -> emitter.line("shr")
            "ushr" -> emitter.line("shr.un")
            "rangeTo" -> TODO("rangeTo not supported in PoC (Phase 9)")
            else -> {
                // Проверим origin ещё раз — может, это PERC (остаток).
                if (origin == "PERC") {
                    emitter.line("rem")
                } else {
                    TODO("Call to '$symbolName' (origin=$origin) not supported in PoC")
                }
            }
        }
    }

    private fun emitCallArguments(call: IrCall, data: Nothing?) {
        call.arguments.forEach { a ->
            if (a != null) emitExpr(a, data)
        }
    }

    private fun emitStdlibCall(call: IrCall, data: Nothing?) {
        val callee = call.symbol.owner as IrSimpleFunction
        val fqName = callee.kotlinFqName.asString()
        // Аргументы: эмитим все non-null arguments (в порядке).
        call.arguments.forEach { a ->
            if (a != null) emitExpr(a, data)
        }
        // Определяем перегрузку по типу аргумента.
        val paramCilTypes = call.arguments.mapNotNull { a ->
            a?.let { TypeMapper.mapType(it.type) }
        }
        val runtimeSig = StdlibResolver.resolveStdlibCall(call, "void", paramCilTypes)
            ?: TODO("Stdlib call $fqName not supported in PoC")
        emitter.line("call $runtimeSig")
    }

    private fun emitBinaryArgs(call: IrCall, data: Nothing?) {
        // Для сравнений: 2 аргумента (arg0, arg1).
        call.arguments.forEach { a ->
            if (a != null) emitExpr(a, data)
        }
    }

    private fun emitComparison(origin: String) {
        // IL: нет прямого opcode "больше". Используем cgt / clt + ceq.
        // a > b  → cgt (pushes 1 if a > b)
        // a < b  → clt
        // a >= b → clt + ldc.i4.0 + ceq  (NOT (a < b))
        // a <= b → cgt + ldc.i4.0 + ceq  (NOT (a > b))
        when (origin) {
            "GT" -> emitter.line("cgt")
            "LT" -> emitter.line("clt")
            "GE" -> {
                emitter.line("clt")
                emitter.line("ldc.i4.0")
                emitter.line("ceq")
            }
            "LE" -> {
                emitter.line("cgt")
                emitter.line("ldc.i4.0")
                emitter.line("ceq")
            }
        }
    }

    private fun emitGetValue(get: IrGetValue) {
        val owner = get.symbol.owner
        when (owner) {
            is IrValueParameter -> {
                emitter.line("ldarg.${owner.indexInParameters}")
            }
            is IrVariable -> {
                val idx = variableIndices[owner]
                    ?: error("Variable ${owner.name} not registered in .locals")
                emitter.line("ldloc.$idx")
            }
            else -> TODO("GetValue of ${owner::class.simpleName} not supported in PoC")
        }
    }

    private fun emitConst(c: IrConst) {
        val v = c.value ?: run {
            emitter.line("ldnull")
            return
        }
        when (c.kind) {
            IrConstKind.Null -> emitter.line("ldnull")
            IrConstKind.Boolean -> {
                if (v as Boolean) emitter.line("ldc.i4.1") else emitter.line("ldc.i4.0")
            }
            IrConstKind.Int -> {
                val i = v as Int
                if (i in -1..8) emitter.line("ldc.i4.$i")
                else emitter.line("ldc.i4 $i")
            }
            IrConstKind.Long -> {
                val l = v as Long
                if (l in 0L..8L) emitter.line("ldc.i4.$l")
                else if (l == -1L) emitter.line("ldc.i4.M1")
                else emitter.line("ldc.i8 $l")
            }
            IrConstKind.Byte -> {
                val b = v as Byte
                if (b in -1..8) emitter.line("ldc.i4.$b")
                else emitter.line("ldc.i4 $b")
            }
            IrConstKind.Short -> {
                val s = v as Short
                if (s in -1..8) emitter.line("ldc.i4.$s")
                else emitter.line("ldc.i4 $s")
            }
            IrConstKind.Char -> emitter.line("ldc.i4 ${v as Char}")
            IrConstKind.Float -> emitter.line("ldc.r4 ${v as Float}")
            IrConstKind.Double -> emitter.line("ldc.r8 ${v as Double}")
            IrConstKind.String -> {
                val s = v as String
                emitter.line("ldstr \"${escapeIlString(s)}\"")
            }
        }
    }

    private fun escapeIlString(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    // === IrWhen / IrBranch ===

    private fun emitWhenAsStatement(w: IrWhen, data: Nothing?) {
        // if/when как statement (если ветки содержат RETURN).
        val endLabel = emitter.newLabel()
        for (branch in w.branches) {
            val cond = branch.condition
            val isTrueConst = (cond is IrConst && cond.kind == IrConstKind.Boolean && cond.value == true)
            if (isTrueConst) {
                // else-ветка (condition = true): emit result, jump to end.
                emitBranchResult(branch.result, data)
                emitter.line("br $endLabel")
                break
            }
            // condition: eval, brfalse nextBranch.
            val nextLabel = emitter.newLabel()
            emitExpr(cond, data)
            emitter.line("brfalse $nextLabel")
            emitBranchResult(branch.result, data)
            emitter.line("br $endLabel")
            emitter.label(nextLabel)
        }
        emitter.label(endLabel)
    }

    private fun emitWhenAsExpression(w: IrWhen, data: Nothing?) {
        // if/when как expression — результат кладётся на стек.
        val origin = w.origin?.debugName
        when (origin) {
            "ANDAND" -> emitAndAnd(w, data)
            "OROR" -> emitOrOr(w, data)
            else -> emitGenericWhenExpression(w, data)
        }
    }

    /** `a && b`: if (!a) false else b. IR: WHEN(branches=[(a, b), (true, false)]). */
    private fun emitAndAnd(w: IrWhen, data: Nothing?) {
        val falseLabel = emitter.newLabel()
        val endLabel = emitter.newLabel()
        val firstBranch = w.branches[0]
        // condition = a (любое boolean expression), if false → result of 2nd branch (false).
        emitExpr(firstBranch.condition, data)
        emitter.line("brfalse $falseLabel")
        // a == true → result = firstBranch.result (b).
        emitExpr(firstBranch.result, data)
        emitter.line("br $endLabel")
        emitter.label(falseLabel)
        // false.
        val secondBranch = w.branches[1]
        emitExpr(secondBranch.result, data)
        emitter.label(endLabel)
    }

    /** `a || b`: if (a) true else b. IR: WHEN(branches=[(a, true), (true, b)]). */
    private fun emitOrOr(w: IrWhen, data: Nothing?) {
        val trueLabel = emitter.newLabel()
        val endLabel = emitter.newLabel()
        val firstBranch = w.branches[0]
        // condition = a (любое boolean expression), if true → result = firstBranch.result (true).
        emitExpr(firstBranch.condition, data)
        emitter.line("brtrue $trueLabel")
        // a == false → result of 2nd branch (b).
        val secondBranch = w.branches[1]
        emitExpr(secondBranch.result, data)
        emitter.line("br $endLabel")
        emitter.label(trueLabel)
        emitExpr(firstBranch.result, data)
        emitter.label(endLabel)
    }

    private fun emitGenericWhenExpression(w: IrWhen, data: Nothing?) {
        val endLabel = emitter.newLabel()
        var first = true
        for (branch in w.branches) {
            val cond = branch.condition
            val isTrueConst = (cond is IrConst && cond.kind == IrConstKind.Boolean && cond.value == true)
            val nextLabel = if (isTrueConst) null else emitter.newLabel()
            if (!isTrueConst) {
                emitExpr(cond, data)
                emitter.line("brfalse $nextLabel")
            }
            // result → push.
            emitExpr(branch.result, data)
            emitter.line("br $endLabel")
            if (nextLabel != null) emitter.label(nextLabel)
        }
        emitter.label(endLabel)
    }

    private fun emitBranchResult(result: IrExpression, data: Nothing?) {
        when (result) {
            is IrBlock -> {
                for (stmt in result.statements) emitStatement(stmt, data)
            }
            is IrReturn -> emitReturn(result, data)
            else -> emitExpr(result, data)
        }
    }
}
