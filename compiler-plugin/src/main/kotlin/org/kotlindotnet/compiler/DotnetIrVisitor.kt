package org.kotlindotnet.compiler

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrScript
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeAlias
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrBreak
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrContinue
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrErrorExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrInstanceInitializerCall
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrReturnableBlock
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.IrSuspendableExpression
import org.jetbrains.kotlin.ir.expressions.IrSyntheticBody
import org.jetbrains.kotlin.ir.expressions.IrThrow
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
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
 * B-01: добавлен scaffolding — `override fun visitXxx` для каждого узла
 * из карты (`TODO/B-visitor-scaffolding/B-01-visitor-node-map.md`).
 * Уже-реализованные узлы делегируют в приватные `emitXxx`; не-реализованные
 * бросают `TODO("[B-01] <узел> — Phase N")` с указанием фазы.
 *
 * Возвращает Unit: IlEmitter накапливает состояние внутри себя.
 *
 * Контекст вызова отслеживается через [MethodContext] (стек методов).
 *
 * Зависит от абстракции [IlEmitter] (A-03), а не от конкретного
 * [TextIlEmitter]. Опкоды эмитятся через типобезопасные методы:
 * `emitter.opcode(IlOpcode.ADD)` для безоперандных, `emitter.ldcI4(n)`,
 * `emitter.ldarg(idx)`, `emitter.br(label)` — для типизированных
 * (короткие формы и экранирование инкапсулированы в [TextIlEmitter]).
 */
class DotnetIrVisitor(
    private val emitter: IlEmitter
) : IrVisitor<Unit, Nothing?>() {

    /** Индексы локальных переменных по их символу (в текущем методе). */
    private val variableIndices = mutableMapOf<IrVariable, Int>()

    // =====================================================================
    // Fallback (последний рубеж — совсем неизвестные узлы).
    // После B-01 почти все узлы имеют специфичный visitXxx, поэтому сюда
    // попадают только действительно экзотические элементы.
    // =====================================================================

    override fun visitElement(element: IrElement, data: Nothing?) {
        TODO("IR element not supported in PoC: ${element::class.simpleName}")
    }

    // =====================================================================
    // Декларации (IrDeclaration)
    // =====================================================================

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

    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?) {
        // top-level function (Phase 4): эмитим.
        // instance / extension (Phase 10): сейчас emitSimpleFunction бросает
        // TODO при наличии dispatchReceiverParameter — оставляем как есть.
        emitSimpleFunction(declaration, data)
    }

    override fun visitClass(declaration: IrClass, data: Nothing?) {
        TODO("[B-01] IrClass — Phase 10")
    }

    override fun visitConstructor(declaration: IrConstructor, data: Nothing?) {
        TODO("[B-01] IrConstructor — Phase 10")
    }

    override fun visitField(declaration: IrField, data: Nothing?) {
        TODO("[B-01] IrField — Phase 10")
    }

    override fun visitProperty(declaration: IrProperty, data: Nothing?) {
        TODO("[B-01] IrProperty — Phase 10")
    }

    override fun visitVariable(declaration: IrVariable, data: Nothing?) {
        emitVariable(declaration, data)
    }

    override fun visitValueParameter(declaration: IrValueParameter, data: Nothing?) {
        // Обрабатывается в emitSimpleFunction (сбор параметров метода) и
        // в emitGetValue (ldarg по индексу). Самостоятельный обход не нужен.
    }

    override fun visitTypeParameter(declaration: IrTypeParameter, data: Nothing?) {
        TODO("[B-01] IrTypeParameter — Phase 12")
    }

    override fun visitTypeAlias(declaration: IrTypeAlias, data: Nothing?) {
        TODO("[B-01] IrTypeAlias — POST-PoC")
    }

    override fun visitEnumEntry(declaration: IrEnumEntry, data: Nothing?) {
        TODO("[B-01] IrEnumEntry — Phase 13")
    }

    override fun visitAnonymousInitializer(declaration: IrAnonymousInitializer, data: Nothing?) {
        TODO("[B-01] IrAnonymousInitializer — Phase 10")
    }

    // Примечание: IrEnumDeclaration в IR-дереве Kotlin — это IrClass с
    // enum-флагом (см. IrClass.hasEnumEntries), отдельного узла нет.
    // Сюда попадает через visitClass (Phase 13).

    override fun visitScript(declaration: IrScript, data: Nothing?) {
        TODO("[B-01] IrScript — POST-PoC")
    }

    // =====================================================================
    // Тела (IrBody)
    // =====================================================================

    override fun visitBlockBody(body: IrBlockBody, data: Nothing?) {
        for (stmt in body.statements) {
            emitStatement(stmt, data)
        }
    }

    override fun visitExpressionBody(body: IrExpressionBody, data: Nothing?) {
        TODO("[B-01] IrExpressionBody — Phase 9")
    }

    override fun visitSyntheticBody(body: IrSyntheticBody, data: Nothing?) {
        TODO("[B-01] IrSyntheticBody — Phase 10+")
    }

    // =====================================================================
    // Выражения (IrExpression) — основной объём scaffolding.
    // Уже-реализованные узлы (Phase 7/8) делегируют в emitXxx.
    // =====================================================================

    override fun visitConst(expression: IrConst, data: Nothing?) {
        emitConst(expression)
    }

    override fun visitCall(expression: IrCall, data: Nothing?) {
        // operator-call (Phase 7) и stdlib-call (Phase 8) — реализованы.
        // user top-level / instance / extension — TODO внутри emitCall.
        emitCall(expression, data)
    }

    override fun visitGetValue(expression: IrGetValue, data: Nothing?) {
        emitGetValue(expression)
    }

    override fun visitSetValue(expression: IrSetValue, data: Nothing?) {
        // locals (Phase 7) — реализованы; field (Phase 10) — TODO внутри emitSetValue.
        emitSetValue(expression, data)
    }

    override fun visitReturn(expression: IrReturn, data: Nothing?) {
        emitReturn(expression, data)
    }

    override fun visitWhen(expression: IrWhen, data: Nothing?) {
        // if/when как statement или expression — диспетчеризация в emitStatement/emitExpr.
        // Прямой обход сюда обычно не доходит, но фреймворк может вызвать.
        emitWhenDispatch(expression, data)
    }

    override fun visitBranch(branch: IrBranch, data: Nothing?) {
        // Часть IrWhen — обрабатывается вместе с ним.
        TODO("[B-01] IrBranch — handled within IrWhen (Phase 7)")
    }

    override fun visitBlock(expression: IrBlock, data: Nothing?) {
        for (stmt in expression.statements) emitStatement(stmt, data)
    }

    // --- Выражения — TODO-заглушки по фазам ---

    override fun visitVararg(expression: IrVararg, data: Nothing?) {
        TODO("[B-01] IrVararg — Phase 11")
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: Nothing?) {
        TODO("[B-01] IrStringConcatenation — Phase 9")
    }

    override fun visitSpreadElement(spread: IrSpreadElement, data: Nothing?) {
        TODO("[B-01] IrSpreadElement — POST-PoC")
    }

    override fun visitBreak(jump: IrBreak, data: Nothing?) {
        TODO("[B-01] IrBreak — Phase 9")
    }

    override fun visitContinue(jump: IrContinue, data: Nothing?) {
        TODO("[B-01] IrContinue — Phase 9")
    }

    override fun visitWhileLoop(loop: IrWhileLoop, data: Nothing?) {
        TODO("[B-01] IrWhileLoop — Phase 9")
    }

    override fun visitDoWhileLoop(loop: IrDoWhileLoop, data: Nothing?) {
        TODO("[B-01] IrDoWhileLoop — Phase 9")
    }

    override fun visitReturnableBlock(expression: IrReturnableBlock, data: Nothing?) {
        TODO("[B-01] IrReturnableBlock — POST-9")
    }

    override fun visitThrow(expression: IrThrow, data: Nothing?) {
        TODO("[B-01] IrThrow — POST-9")
    }

    override fun visitTry(aTry: IrTry, data: Nothing?) {
        TODO("[B-01] IrTry — POST-9")
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: Nothing?) {
        TODO("[B-01] IrTypeOperatorCall — POST-9")
    }

    override fun visitFunctionReference(expression: IrFunctionReference, data: Nothing?) {
        TODO("[B-01] IrFunctionReference — G-01")
    }

    override fun visitPropertyReference(expression: IrPropertyReference, data: Nothing?) {
        TODO("[B-01] IrPropertyReference — POST-PoC")
    }

    override fun visitClassReference(expression: IrClassReference, data: Nothing?) {
        TODO("[B-01] IrClassReference — POST-PoC")
    }

    override fun visitGetField(expression: IrGetField, data: Nothing?) {
        TODO("[B-01] IrGetField — Phase 10")
    }

    override fun visitSetField(expression: IrSetField, data: Nothing?) {
        TODO("[B-01] IrSetField — Phase 10")
    }

    override fun visitGetObjectValue(expression: IrGetObjectValue, data: Nothing?) {
        TODO("[B-01] IrGetObjectValue — Phase 13")
    }

    override fun visitGetEnumValue(expression: IrGetEnumValue, data: Nothing?) {
        TODO("[B-01] IrGetEnumValue — Phase 13")
    }

    override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: Nothing?) {
        TODO("[B-01] IrInstanceInitializerCall — Phase 10")
    }

    // Примечание: IrNewInstance в IR-дереве Kotlin — это IrConstructorCall
    // (visitConstructorCall). Отдельного IrNewInstance-узла нет.
    override fun visitConstructorCall(expression: IrConstructorCall, data: Nothing?) {
        TODO("[B-01] IrConstructorCall (newobj) — Phase 10")
    }

    // Примечание: IrNewArray в IR-дереве Kotlin отсутствует — массивы
    // создаются через IrVararg (Phase 11) или IrConstantArray. См. карту.

    override fun visitFunctionExpression(expression: IrFunctionExpression, data: Nothing?) {
        TODO("[B-01] IrFunctionExpression — G-01")
    }

    override fun visitSuspendableExpression(expression: IrSuspendableExpression, data: Nothing?) {
        TODO("[B-01] IrSuspendableExpression — POST-PoC")
    }

    override fun visitErrorExpression(expression: IrErrorExpression, data: Nothing?) {
        TODO("[B-01] IrErrorExpression — n/a (compile error recovery, skip)")
    }

    // =====================================================================
    // Приватные emit-методы (реализация уже-обработанных узлов).
    // =====================================================================

    private fun emitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?) {
        if (declaration.dispatchReceiverParameter != null) {
            TODO("[B-01] IrSimpleFunction (instance) — Phase 10")
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
            emitter.opcode(IlOpcode.RET)
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
            emitter.opcode(IlOpcode.RET)
        } else {
            emitExpr(ret.value, data)
            emitter.opcode(IlOpcode.RET)
        }
    }

    private fun emitVariable(v: IrVariable, data: Nothing?) {
        val idx = variableIndices[v]
            ?: error("Variable ${v.name} not registered in .locals")
        v.initializer?.let { init ->
            emitExpr(init, data)
            emitter.stloc(idx)
        }
    }

    private fun emitSetValue(s: IrSetValue, data: Nothing?) {
        emitExpr(s.value, data)
        val owner = s.symbol.owner
        when (owner) {
            is IrVariable -> {
                val idx = variableIndices[owner]
                    ?: error("Variable ${owner.name} not registered in .locals")
                emitter.stloc(idx)
            }
            is IrField -> TODO("[B-01] IrSetValue (field) — Phase 10")
            else -> TODO("[B-01] IrSetValue of ${owner::class.simpleName} — Phase 10")
        }
    }

    private fun emitCall(call: IrCall, data: Nothing?) {
        val origin = call.origin?.debugName

        // Сначала обрабатываем special origins (сравнения, логические),
        // т.к. у них своя IL-семантика.
        when (origin) {
            IrOrigins.GT, IrOrigins.LT, IrOrigins.GE, IrOrigins.LE -> {
                emitBinaryArgs(call, data)
                emitComparison(origin)
                return
            }
            IrOrigins.EQEQ, IrOrigins.EQEQEQ -> {
                emitBinaryArgs(call, data)
                emitter.opcode(IlOpcode.CEQ)
                return
            }
            IrOrigins.ANDAND, IrOrigins.OROR -> {
                // На самом деле && / || в Kotlin lowering превращается в IrWhen
                // (см. дамп test_bool). Если всё же пришли как IrCall — fallback.
                TODO("ANDAND/OROR expected to be IrWhen, got IrCall")
            }
        }

        // Stdlib-вызовы (println, print) → KotlinDotnetRuntime.
        // Проверка делегирована в StdlibResolver (A-05, см. D-02).
        if (StdlibResolver.isStdlibCall(call)) {
            emitStdlibCall(call, data)
            return
        }

        // Обычный operator-call: эмитим аргументы, затем opcode.
        emitCallArguments(call, data)

        // Унарные / бинарные операторы.
        val symbolName = call.symbol.owner.name.asString()
        when (symbolName) {
            KotlinOperators.PLUS -> emitter.opcode(IlOpcode.ADD)
            KotlinOperators.MINUS -> emitter.opcode(IlOpcode.SUB)
            KotlinOperators.TIMES -> emitter.opcode(IlOpcode.MUL)
            KotlinOperators.DIV -> emitter.opcode(IlOpcode.DIV)
            KotlinOperators.REM -> emitter.opcode(IlOpcode.REM)
            KotlinOperators.UNARY_MINUS -> emitter.opcode(IlOpcode.NEG)
            KotlinOperators.UNARY_PLUS -> Unit // nop
            KotlinOperators.NOT -> {
                // Для Boolean: not это логическое НЕ, не побитовое.
                // not(true)=1 → 0xFFFFFFFE (побитово), а нужно 0.
                // Используем ldc.i4.0 + ceq (logical NOT).
                emitter.ldcI4(0)
                emitter.opcode(IlOpcode.CEQ)
            }
            KotlinOperators.AND -> emitter.opcode(IlOpcode.AND)
            KotlinOperators.OR -> emitter.opcode(IlOpcode.OR)
            KotlinOperators.XOR -> emitter.opcode(IlOpcode.XOR)
            KotlinOperators.SHL -> emitter.opcode(IlOpcode.SHL)
            KotlinOperators.SHR -> emitter.opcode(IlOpcode.SHR)
            KotlinOperators.USHR -> emitter.opcode(IlOpcode.SHR_UN)
            KotlinOperators.RANGE_TO -> TODO("[B-01] IrCall RANGE_TO — Phase 9")
            else -> {
                // Не operator-call — значит это пользовательский вызов функции.
                // dispatchReceiverParameter == null → top-level (Phase 9);
                // иначе instance/extension (Phase 10).
                val owner = call.symbol.owner
                if (owner.dispatchReceiverParameter == null) {
                    TODO("[B-01] IrCall (user, top-level) — Phase 9")
                } else {
                    TODO("[B-01] IrCall (instance/extension) — Phase 10")
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
        emitter.opcode(IlOpcode.CALL, runtimeSig)
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
            IrOrigins.GT -> emitter.opcode(IlOpcode.CGT)
            IrOrigins.LT -> emitter.opcode(IlOpcode.CLT)
            IrOrigins.GE -> {
                emitter.opcode(IlOpcode.CLT)
                emitter.ldcI4(0)
                emitter.opcode(IlOpcode.CEQ)
            }
            IrOrigins.LE -> {
                emitter.opcode(IlOpcode.CGT)
                emitter.ldcI4(0)
                emitter.opcode(IlOpcode.CEQ)
            }
            else -> TODO("Unsupported comparison origin: $origin (PoC supports GT/LT/GE/EQEQ)")
        }
    }

    private fun emitGetValue(get: IrGetValue) {
        val owner = get.symbol.owner
        when (owner) {
            is IrValueParameter -> {
                emitter.ldarg(owner.indexInParameters)
            }
            is IrVariable -> {
                val idx = variableIndices[owner]
                    ?: error("Variable ${owner.name} not registered in .locals")
                emitter.ldloc(idx)
            }
            else -> TODO("GetValue of ${owner::class.simpleName} not supported in PoC")
        }
    }

    private fun emitConst(c: IrConst) {
        val v = c.value ?: run {
            emitter.opcode(IlOpcode.LDNULL)
            return
        }
        when (c.kind) {
            IrConstKind.Null -> emitter.opcode(IlOpcode.LDNULL)
            IrConstKind.Boolean -> {
                // Boolean true/false → ldc.i4.1 / ldc.i4.0.
                if (v as Boolean) emitter.ldcI4(1) else emitter.ldcI4(0)
            }
            IrConstKind.Int -> emitter.ldcI4(v as Int)
            IrConstKind.Long -> emitter.ldcI8(v as Long)
            IrConstKind.Byte -> emitter.ldcI4((v as Byte).toInt())
            IrConstKind.Short -> emitter.ldcI4((v as Short).toInt())
            IrConstKind.Char -> emitter.ldcI4((v as Char).code)
            IrConstKind.Float -> emitter.ldcR4(v as Float)
            IrConstKind.Double -> emitter.ldcR8(v as Double)
            IrConstKind.String -> emitter.ldstr(v as String)
        }
    }

    // === IrWhen / IrBranch ===

    /**
     * Диспетчеризация IrWhen: как statement (с RETURN) или как expression.
     * Вызывается из [visitWhen], если фреймворк обходит напрямую.
     */
    private fun emitWhenDispatch(w: IrWhen, data: Nothing?) {
        // Если результат unit-типа — statement, иначе expression.
        if (w.type.isUnit()) {
            emitWhenAsStatement(w, data)
        } else {
            emitWhenAsExpression(w, data)
        }
    }

    private fun emitWhenAsStatement(w: IrWhen, data: Nothing?) {
        // if/when как statement (если ветки содержат RETURN).
        val endLabel = emitter.newLabel()
        for (branch in w.branches) {
            val cond = branch.condition
            val isTrueConst = (cond is IrConst && cond.kind == IrConstKind.Boolean && cond.value == true)
            if (isTrueConst) {
                // else-ветка (condition = true): emit result, jump to end.
                emitBranchResult(branch.result, data)
                emitter.br(endLabel)
                break
            }
            // condition: eval, brfalse nextBranch.
            val nextLabel = emitter.newLabel()
            emitExpr(cond, data)
            emitter.brfalse(nextLabel)
            emitBranchResult(branch.result, data)
            emitter.br(endLabel)
            emitter.label(nextLabel)
        }
        emitter.label(endLabel)
    }

    private fun emitWhenAsExpression(w: IrWhen, data: Nothing?) {
        // if/when как expression — результат кладётся на стек.
        val origin = w.origin?.debugName
        when (origin) {
            IrOrigins.ANDAND -> emitAndAnd(w, data)
            IrOrigins.OROR -> emitOrOr(w, data)
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
        emitter.brfalse(falseLabel)
        // a == true → result = firstBranch.result (b).
        emitExpr(firstBranch.result, data)
        emitter.br(endLabel)
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
        emitter.brtrue(trueLabel)
        // a == false → result of 2nd branch (b).
        val secondBranch = w.branches[1]
        emitExpr(secondBranch.result, data)
        emitter.br(endLabel)
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
                emitter.brfalse(nextLabel!!)
            }
            // result → push.
            emitExpr(branch.result, data)
            emitter.br(endLabel)
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
