package org.kotlindotnet.compiler

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
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
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrContinue
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
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
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrReturnableBlock
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.IrSuspendableExpression
import org.jetbrains.kotlin.ir.expressions.IrSyntheticBody
import org.jetbrains.kotlin.ir.expressions.IrThrow
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isAny
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.kotlindotnet.compiler.il.IlEmitter
import org.kotlindotnet.compiler.il.IlOpcode
import org.kotlindotnet.compiler.ir.IrOrigins
import org.kotlindotnet.compiler.ir.KotlinOperators

/**
 * Посетитель IR-дерева, эмитящий IL-текст.
 *
 * PoC Phase 7–9: покрывает:
 * - `IrFile → IrSimpleFunction(top-level) → IrBlockBody`
 * - `IrReturn`, `IrCall` (operator + сравнения + stdlib + user top-level),
 *   `IrGetValue`, `IrConst`
 * - `IrVariable` (локальные переменные, `val`/`var`)
 * - `IrSetValue` (присваивание локалке)
 * - `IrWhen` + `IrBranch` (if/when/ANDAND/OROR)
 * - `IrBlock` (блок выражений), `IrComposite`
 * - **Phase 9:** `IrWhileLoop`, `IrDoWhileLoop`, `IrBreak`, `IrContinue`
 *   (LoopContext-стек), `IrStringConcatenation` (`string.Concat`),
 *   `IrTypeOperatorCall` (`IMPLICIT_COERCION_TO_UNIT` — вычислить и отбросить),
 *   operator `inc`/`dec` (→ `±1` для примитивов), вызовы пользовательских
 *   top-level функций.
 *
 * B-01: добавлен scaffolding — `override fun visitXxx` для каждого узла
 * из карты (`TODO/B-visitor-scaffolding/B-01-visitor-node-map.md`).
 * Уже-реализованные узлы делегируют в приватные `emitXxx`; не-реализованные
 * бросают `TODO("[B-01] <узел> — Phase N")` с указанием фазы.
 *
 * Возвращает Unit: IlEmitter накапливает состояние внутри себя.
 *
 * Контекст вызова отслеживается через [MethodContext] (стек методов);
 * контекст циклов (break/continue) — через [loopContexts] (стек
 * [LoopContext]).
 *
 * Зависит от абстракции [IlEmitter] (A-03). Опкоды эмитятся через
 * типобезопасные методы: `emitter.opcode(IlOpcode.ADD)` для безоперандных,
 * `emitter.ldcI4(n)`, `emitter.ldarg(idx)`, `emitter.br(label)` — для
 * типизированных (короткие формы инкапсулированы в реализации эмиттера).
 */
class DotnetIrVisitor(
    private val emitter: IlEmitter
) : IrVisitor<Unit, Nothing?>() {

    /** Индексы локальных переменных по их символу (в текущем методе). */
    private val variableIndices = mutableMapOf<IrVariable, Int>()

    /**
     * Стек контекстов циклов для break/continue (Phase 9).
     *
     * `continueLabel` — метка, куда прыгает `continue` (начало следующей
     * итерации: для while это проверка условия, для do-while — тело).
     * `breakLabel` — метка выхода из цикла.
     * `loop` — символ [IrLoop], чтобы [IrBreak]/[IrContinue] находили свой
     * контекст (для labeled break/continue — Phase 9.x, пока unlabeled).
     */
    private data class LoopContext(
        val continueLabel: String,
        val breakLabel: String,
        val loop: IrLoop,
    )

    private val loopContexts = ArrayDeque<LoopContext>()

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

        // Двухпроходный порядок (Phase 10): сначала все top-level функции в
        // контейнерный класс, затем пользовательские классы. Это гарантирует
        // смежность MethodDef-строк каждой группы типов (требование ECMA
        // для fieldList/methodList диапазонов). Финализация (pe-бэкенд)
        // происходит в endContainerClass — классы регистрируются ДО неё.
        emitter.beginContainerClass(ns, cls)
        for (decl in declaration.declarations) {
            if (decl !is IrSimpleFunction) continue
            if (decl.dispatchReceiverParameter != null) continue // члены классов — во 2-м проходе
            emitSimpleFunction(decl, data)
        }
        for (decl in declaration.declarations) {
            if (decl is IrClass) emitClass(decl, data)
        }
        emitter.endContainerClass()
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?) {
        // top-level (Phase 4) и instance-методы/аксессоры (Phase 10):
        // различение внутри emitSimpleFunction по dispatchReceiverParameter.
        emitSimpleFunction(declaration, data)
    }

    override fun visitClass(declaration: IrClass, data: Nothing?) {
        emitClass(declaration, data)
    }

    override fun visitConstructor(declaration: IrConstructor, data: Nothing?) {
        emitConstructor(declaration, data)
    }

    override fun visitField(declaration: IrField, data: Nothing?) {
        // Поля объявляются в emitClass (declareField); самостоятельного
        // обхода IrField нет — инициализаторы инжектируются в primary ctor.
    }

    override fun visitProperty(declaration: IrProperty, data: Nothing?) {
        // Свойства обрабатываются в emitClass: бэкинг-поле → declareField,
        // дефолтные аксессоры → обычные instance-методы (наблюдения 10.0).
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
        // Пустые init-блоки — no-op (наблюдения 10.0: K2 кладёт их в primary
        // ctor; инициализаторы свойств мы инжектируем отдельно).
        val hasStatements = (declaration.body as? IrBlockBody)?.statements?.isNotEmpty() == true
        if (hasStatements) {
            TODO("[B-01] IrAnonymousInitializer (non-empty) — Phase 10.x")
        }
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
        // for-loop lowering приходит как IrBlock с origin=FOR_LOOP (см. дамп).
        // Phase 9 не реализует for (требует итераторы — Phase 11 / G-02).
        if (expression.origin?.debugName == "FOR_LOOP") {
            TODO("[B-01] for-loop — Phase 11/G-02 (requires iterators)")
        }
        for (stmt in expression.statements) emitStatement(stmt, data)
    }

    override fun visitComposite(expression: IrComposite, data: Nothing?) {
        // IrComposite — последовательность выражений, результат = последнее.
        // В statement-context (циклы, блоки) — эмитим все стейтменты;
        // значение последнего отбрасывается вызывающим (pop при необходимости).
        for (stmt in expression.statements) emitStatement(stmt, data)
    }

    // --- Выражения — TODO-заглушки по фазам ---

    override fun visitVararg(expression: IrVararg, data: Nothing?) {
        TODO("[B-01] IrVararg — Phase 11")
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: Nothing?) {
        emitStringConcatenation(expression, data)
    }

    override fun visitSpreadElement(spread: IrSpreadElement, data: Nothing?) {
        TODO("[B-01] IrSpreadElement — POST-PoC")
    }

    override fun visitBreak(jump: IrBreak, data: Nothing?) {
        emitBreakContinue(jump.loop, isBreak = true, jump.label)
    }

    override fun visitContinue(jump: IrContinue, data: Nothing?) {
        emitBreakContinue(jump.loop, isBreak = false, jump.label)
    }

    override fun visitWhileLoop(loop: IrWhileLoop, data: Nothing?) {
        emitWhileLoop(loop, data)
    }

    override fun visitDoWhileLoop(loop: IrDoWhileLoop, data: Nothing?) {
        emitDoWhileLoop(loop, data)
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
        emitTypeOperator(expression, data)
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
        emitGetField(expression, data)
    }

    override fun visitSetField(expression: IrSetField, data: Nothing?) {
        emitSetField(expression, data)
    }

    override fun visitGetObjectValue(expression: IrGetObjectValue, data: Nothing?) {
        TODO("[B-01] IrGetObjectValue — Phase 13")
    }

    override fun visitGetEnumValue(expression: IrGetEnumValue, data: Nothing?) {
        TODO("[B-01] IrGetEnumValue — Phase 13")
    }

    override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: Nothing?) {
        // Маркер инициализации экземпляра в primary ctor (наблюдения 10.0):
        // для нас no-op — аллокация происходит в newobj на месте вызова.
    }

    // Примечание: IrNewInstance в IR-дереве Kotlin — это IrConstructorCall
    // (visitConstructorCall). Отдельного IrNewInstance-узла нет.
    override fun visitConstructorCall(expression: IrConstructorCall, data: Nothing?) {
        emitConstructorCall(expression, data)
    }

    override fun visitDelegatingConstructorCall(
        expression: IrDelegatingConstructorCall,
        data: Nothing?,
    ) {
        emitDelegatingConstructorCall(expression, data)
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

    private fun emitSimpleFunction(
        declaration: IrSimpleFunction,
        data: Nothing?,
        attributesOverride: UInt? = null,
    ) {
        val isInstance = declaration.dispatchReceiverParameter != null

        // Сброс состояния для нового метода.
        variableIndices.clear()
        emitter.resetMethodState()

        val name = NameMapper.methodName(declaration)
        val retType = cilTypeOf(declaration.returnType)
        val params = declaration.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .map { p: IrValueParameter ->
                cilTypeOf(p.type) to p.name.asString()
            }

        // main() → .entrypoint (Phase 8); только статический main (Phase 10).
        val isEntrypoint = !isInstance && name == "main"
        emitter.beginMethod(
            name, retType, params,
            isStatic = !isInstance,
            isEntrypoint = isEntrypoint,
            attributesOverride = attributesOverride,
        )

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

        emitter.endMethod()
    }

    // =====================================================================
    // Классы (Phase 10): emitClass / конструкторы / поля / instance-вызовы.
    // Фактические IR-формы — в TODO/PHASE-10.md §«10.0 Наблюдения».
    // =====================================================================

    /**
     * Пользовательский класс → beginClass + поля + конструкторы/методы.
     *
     * FAKE_OVERRIDE-члены (equals/hashCode/toString и унаследованные)
     * пропускаются — у них нет тел, реальные реализации живут в предке.
     */
    private fun emitClass(declaration: IrClass, data: Nothing?) {
        if (declaration.isInterface) {
            emitInterface(declaration)
            return
        }

        val file = declaration.parent as? IrFile
            ?: error("Class ${declaration.name} is not top-level (parent is ${declaration.parent::class.simpleName})")
        val ns = NameMapper.namespace(file)

        var baseCil: String? = null
        val interfaces = mutableListOf<String>()
        for (superType in declaration.superTypes) {
            if (superType.isAny()) continue
            val owner = superType.classOrNull?.owner as? IrClass
            if (owner?.isInterface == true) {
                interfaces.add(cilTypeOf(superType))
            } else if (baseCil == null) {
                baseCil = cilTypeOf(superType)
            }
        }
        emitter.beginClass(ns, NameMapper.className(declaration), classFlags(declaration), baseCil, interfaces)

        // Бэкинг-поля свойств + их дефолтные аксессоры (наблюдения 10.0:
        // FIELD PROPERTY_BACKING_FIELD; аксессоры висят на свойстве,
        // в class.declarations их нет).
        for (prop in declaration.declarations.filterIsInstance<IrProperty>()) {
            if (prop.origin == IrDeclarationOrigin.FAKE_OVERRIDE) continue
            val backingField = prop.backingField
            if (backingField != null) {
                emitter.declareField(cilTypeOf(backingField.type), backingField.name.asString(), backingField.isStatic)
            }
            // Дефолтные аксессоры эмитим как обычные instance-методы.
            for (accessor in listOf(prop.getter, prop.setter)) {
                if (accessor == null) continue
                if (accessor.body != null && accessor.origin != IrDeclarationOrigin.FAKE_OVERRIDE) {
                    emitSimpleFunction(accessor, data, memberAttributes(accessor, declaration))
                }
            }
        }
        // Прямые декларации полей (без свойства) — на всякий случай.
        for (field in declaration.declarations.filterIsInstance<IrField>()) {
            emitter.declareField(cilTypeOf(field.type), field.name.asString(), field.isStatic)
        }

        for (member in declaration.declarations) {
            when (member) {
                is IrConstructor -> emitConstructor(member, data)
                is IrSimpleFunction ->
                    // FAKE_OVERRIDE не имеют тел; дефолтные аксессоры и обычные
                    // методы эмитятся единым instance-путём.
                    if (member.body != null && member.origin != IrDeclarationOrigin.FAKE_OVERRIDE) {
                        emitSimpleFunction(member, data, memberAttributes(member, declaration))
                    }
                else -> Unit
            }
        }

        emitter.endClass()
    }

    /** Пустой интерфейс → TypeDef с Interface|Abstract; члены с телами — Phase 13. */
    private fun emitInterface(declaration: IrClass) {
        val file = declaration.parent as? IrFile
            ?: error("Interface ${declaration.name} is not top-level")
        val ns = NameMapper.namespace(file)
        // Public | Interface | Abstract (TypeAttributes.Public = 0x1!);
        // extends = nil (см. наблюдение P4).
        emitter.beginClass(ns, NameMapper.className(declaration), 0x00A1u, null, emptyList())
        for (member in declaration.declarations) {
            when (member) {
                is IrSimpleFunction ->
                    if (member.body != null && member.origin != IrDeclarationOrigin.FAKE_OVERRIDE) {
                        TODO("[B-01] interface member with body (${member.name}) — Phase 13")
                    }
                else -> Unit
            }
        }
        emitter.endClass()
    }

    /**
     * Конструктор класса. Тело уже содержит DELEGATING_CONSTRUCTOR_CALL
     * (+ INSTANCE_INITIALIZER_CALL — no-op). После delegating call инжектируем
     * инициализаторы бэкинг-полей (наблюдения 10.0: они висят на
     * FIELD.EXPRESSION_BODY и НЕ присутствуют в теле ctor).
     *
     * Инжекция — только в primary ctor: secondary делегируют в this(...),
     * и инициализация выполняется в цепочке ровно один раз.
     */
    private fun emitConstructor(declaration: IrConstructor, data: Nothing?) {
        variableIndices.clear()
        emitter.resetMethodState()

        val params = declaration.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .map { p: IrValueParameter -> cilTypeOf(p.type) to p.name.asString() }
        emitter.beginConstructor(params)

        val body = declaration.body ?: error("Constructor of ${declaration.parent} has no body")
        collectLocals(body)
        emitter.emitLocalsIfAny()

        emitBody(body, data)

        if (declaration.isPrimary) {
            val owningClass = declaration.parent as IrClass
            for (prop in owningClass.declarations.filterIsInstance<IrProperty>()) {
                if (prop.origin == IrDeclarationOrigin.FAKE_OVERRIDE) continue
                val backingField = prop.backingField ?: continue
                val initializer = backingField.initializer ?: continue
                emitter.ldarg(0)
                emitExpr(initializer.expression, data)
                emitter.opcode(IlOpcode.STFLD, fieldRef(backingField))
            }
        }

        emitter.opcode(IlOpcode.RET)
        emitter.endMethod()
    }

    /**
     * `Point(1, 2)` → args + NEWOBJ `.ctor`. Слота receiver нет
     * (наблюдения 10.0): аллокацию делает сам newobj.
     */
    private fun emitConstructorCall(call: IrConstructorCall, data: Nothing?) {
        call.arguments.forEach { a -> a?.let { emitExpr(it, data) } }
        emitter.opcode(IlOpcode.NEWOBJ, constructorRef(call.symbol.owner))
    }

    /**
     * Делегирующий вызов конструктора (base или this). Слота receiver тоже
     * нет — `this` подаётся явно через ldarg.0 перед аргументами.
     */
    private fun emitDelegatingConstructorCall(
        call: IrDelegatingConstructorCall,
        data: Nothing?,
    ) {
        emitter.ldarg(0)
        call.arguments.forEach { a -> a?.let { emitExpr(it, data) } }
        emitter.opcode(IlOpcode.CALL, constructorRef(call.symbol.owner))
    }

    /** `ldfld <field-ref>`: receiver на стеке из [expression.receiver]. */
    private fun emitGetField(expression: IrGetField, data: Nothing?) {
        val field = expression.symbol.owner
        if (field.isStatic) {
            TODO("[B-01] static field access (ldsfld) — Phase 13")
        }
        val receiver = expression.receiver
            ?: error("IrGetField without receiver: ${field.name}")
        emitExpr(receiver, data)
        emitter.opcode(IlOpcode.LDFLD, fieldRef(field))
    }

    /** `stfld <field-ref>`: порядок стека — object, value. */
    private fun emitSetField(expression: IrSetField, data: Nothing?) {
        val field = expression.symbol.owner
        if (field.isStatic) {
            TODO("[B-01] static field access (stsfld) — Phase 13")
        }
        val receiver = expression.receiver
            ?: error("IrSetField without receiver: ${field.name}")
        emitExpr(receiver, data)
        emitExpr(expression.value, data)
        emitter.opcode(IlOpcode.STFLD, fieldRef(field))
    }

    /**
     * Instance-call → `callvirt`. Dispatch receiver занимает `arguments[0]`
     * (наблюдения 10.0). Ref строится по declaring-классу реальной
     * реализации: fake overrides (без тела) спускаются по overriddenSymbols
     * (наблюдение P3: чтение унаследованного свойства резолвится в fake
     * override производного класса).
     */
    private fun emitInstanceCall(call: IrCall, data: Nothing?) {
        val callee = call.symbol.owner as IrSimpleFunction
        val realCallee = resolveRealOverride(callee)

        val dispatch = call.dispatchReceiver
            ?: error("Instance call without dispatch receiver: ${callee.name}")
        emitExpr(dispatch, data)

        // Остальные слоты аргументов после dispatch receiver.
        for (i in 1 until call.arguments.size) {
            call.arguments[i]?.let { emitExpr(it, data) }
        }

        val declaringClass = realCallee.parent as? IrClass
            ?: error("Callee ${realCallee.name} has no IrClass parent")
        val paramSig = realCallee.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .joinToString(", ") { cilTypeOf(it.type) }
        val retCil = cilTypeOf(realCallee.returnType)
        val methodName = NameMapper.methodName(realCallee)
        val ref =
            "$retCil instance ${qualifiedClassName(declaringClass)}::$methodName($paramSig)"
        emitter.opcode(IlOpcode.CALLVIRT, ref)
    }

    /** Спуск по overriddenSymbols до функции с телом (fake overrides без тела). */
    private fun resolveRealOverride(fn: IrSimpleFunction): IrSimpleFunction {
        var currentFn = fn
        var hops = 0
        while (currentFn.body == null) {
            val next = currentFn.overriddenSymbols.firstOrNull()?.owner ?: break
            currentFn = next
            if (++hops > 64) error("overriddenSymbols chain too deep at ${fn.name}")
        }
        return currentFn
    }

    /**
     * MethodAttributes для open/override (10.7).
     *
     * override → Public|HideBySig|Virtual (ReuseSlot, 0x00C6) — ОБЯЗАТЕЛЬНО
     * виртуальный даже в final-классе, иначе метод скрывает слот базы и
     * полиморфизм через базовую переменную ломается;
     * open в неоткрытом-для-наследования классе → дефолт (некому переопределять);
     * open в открытом классе → + NewSlot (0x01C6).
     */
    private fun memberAttributes(fn: IrSimpleFunction, containingClass: IrClass): UInt? =
        when {
            fn.overriddenSymbols.isNotEmpty() -> 0x000000C6u
            containingClass.modality != Modality.FINAL && fn.modality == Modality.OPEN -> 0x000001C6u
            else -> null
        }

    // === Хелперы классов ===

    /**
     * TypeAttributes. ВАЖНО: Public для типов = 0x00000001 (маска
     * видимости 0x7), не путать с MethodAttributes.Public = 0x6.
     * final → Public|Sealed|BeforeFieldInit, open → Public|BeforeFieldInit.
     */
    private fun classFlags(declaration: IrClass): UInt =
        when (declaration.modality) {
            Modality.FINAL -> 0x00100101u // Public | Sealed | BeforeFieldInit
            Modality.OPEN -> 0x00100001u // Public | BeforeFieldInit
            else -> TODO("[B-01] class modality ${declaration.modality} — Phase 13")
        }

    /** CIL-тип: примитивы через TypeMapper, пользовательские классы — fqName verbatim. */
    private fun cilTypeOf(type: IrType): String {
        val mapped = TypeMapper.mapType(type)
        if (mapped != "object") return mapped
        // "object" — это kotlin.Any/Any? либо пользовательский класс.
        val owner = type.classOrNull?.owner as? IrClass ?: return "object"
        val fqName = owner.kotlinFqName.asString()
        return if (fqName.startsWith("kotlin.") || fqName.isEmpty()) "object" else fqName
    }

    /** Полное CIL-имя класса (`probe.p1.Point`). */
    private fun qualifiedClassName(declaration: IrClass): String =
        declaration.kotlinFqName.asString()

    /** Field-ref для ldfld/stfld: `<cilType> <Ns.Class>::<field>`. */
    private fun fieldRef(field: IrField): String {
        val owner = field.parent as? IrClass
            ?: error("Field ${field.name} has no IrClass parent")
        return "${cilTypeOf(field.type)} ${qualifiedClassName(owner)}::${field.name.asString()}"
    }

    /** Ctor-ref для call/newobj: `void instance <Ns.Class>::.ctor(<params>)`. */
    private fun constructorRef(constructor: IrConstructor): String {
        val owner = constructor.parent as? IrClass
            ?: error("Constructor has no IrClass parent")
        val paramSig = constructor.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .joinToString(", ") { cilTypeOf(it.type) }
        // Базовый ctor kotlin.Any → BCL System.Object (вне нашей сборки).
        val typeName =
            if (owner.kotlinFqName.asString() == "kotlin.Any") "[System.Runtime]System.Object"
            else qualifiedClassName(owner)
        return "void instance $typeName::.ctor($paramSig)"
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
            is IrWhileLoop -> {
                collectLocals(element.condition)
                element.body?.let { collectLocals(it) }
            }
            is IrDoWhileLoop -> {
                element.body?.let { collectLocals(it) }
                collectLocals(element.condition)
            }
            is IrTypeOperatorCall -> collectLocals(element.argument)
            is IrStringConcatenation -> element.arguments.forEach { collectLocals(it) }
            is IrComposite -> element.statements.forEach { collectLocals(it) }
            is IrConstructorCall -> element.arguments.forEach { a -> a?.let { collectLocals(it) } }
            is IrDelegatingConstructorCall -> element.arguments.forEach { a -> a?.let { collectLocals(it) } }
            is IrGetField -> element.receiver?.let { collectLocals(it) }
            is IrSetField -> {
                element.receiver?.let { collectLocals(it) }
                collectLocals(element.value)
            }
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
            is IrWhileLoop -> emitWhileLoop(e, data)
            is IrDoWhileLoop -> emitDoWhileLoop(e, data)
            is IrBreak -> emitBreakContinue(e.loop, isBreak = true, e.label)
            is IrContinue -> emitBreakContinue(e.loop, isBreak = false, e.label)
            is IrTypeOperatorCall -> emitTypeOperator(e, data)
            is IrStringConcatenation -> emitStringConcatenation(e, data)
            is IrBlock -> {
                if (e.origin?.debugName == "FOR_LOOP") {
                    TODO("[B-01] for-loop — Phase 11/G-02 (requires iterators)")
                }
                for (stmt in e.statements) emitStatement(stmt, data)
            }
            is IrComposite -> {
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
                if (e.origin?.debugName == "FOR_LOOP") {
                    TODO("[B-01] for-loop — Phase 11/G-02 (requires iterators)")
                }
                for (stmt in e.statements) emitStatement(stmt, data)
            }
            is IrComposite -> {
                for (stmt in e.statements) emitStatement(stmt, data)
            }
            is IrSetValue -> emitSetValue(e, data)
            is IrStringConcatenation -> emitStringConcatenation(e, data)
            is IrTypeOperatorCall -> emitTypeOperator(e, data)
            is IrWhileLoop -> emitWhileLoop(e, data)
            is IrDoWhileLoop -> emitDoWhileLoop(e, data)
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
            is IrField -> TODO("[B-01] IrSetValue (field) — n/a in K2 IR: use IrSetField")
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

        val symbolName = call.symbol.owner.name.asString()

        // Пользовательский вызов (top-level Phase 9 / instance Phase 10):
        // аргументы эмитит соответствующий обработчик — у instance-call
        // dispatch receiver занимает слот 0 в arguments.
        //
        // Операторные имена различаем по объявлению вызываемого: plus/inc/...
        // обрабатываются как IL-опкоды только если функция объявлена в
        // kotlin.* (методы примитивов). Пользовательский `class C { fun inc() }`
        // — обычный instance-вызов.
        val calleeFqName =
            (call.symbol.owner as? IrSimpleFunction)?.kotlinFqName?.asString() ?: ""
        val isStdlibOperator = calleeFqName.startsWith("kotlin.") && symbolName in KotlinOperators.ALL

        if (!isStdlibOperator) {
            val owner = call.symbol.owner
            if (owner.dispatchReceiverParameter == null) {
                emitCallArguments(call, data)
                emitUserTopLevelCall(call, data)
            } else {
                emitInstanceCall(call, data)
            }
            return
        }

        // Обычный operator-call: эмитим аргументы, затем opcode.
        emitCallArguments(call, data)

        // Унарные / бинарные операторы.
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
            KotlinOperators.INC -> {
                // a.inc() → a + 1 (для примитивов). Аргумент <this> уже на стеке.
                emitter.ldcI4(1)
                emitter.opcode(IlOpcode.ADD)
            }
            KotlinOperators.DEC -> {
                // a.dec() → a - 1 (для примитивов). Аргумент <this> уже на стеке.
                emitter.ldcI4(1)
                emitter.opcode(IlOpcode.SUB)
            }
            KotlinOperators.RANGE_TO -> TODO("[B-01] IrCall RANGE_TO — Phase 11 (ranges)")
            else -> {
                // Не operator-call — сюда не доходим: пользовательские вызовы
                // обработаны выше (см. проверку OPERATOR_SYMBOLS).
                error("Unexpected call symbol '$symbolName' in operator dispatch")
            }
        }
    }

    /**
     * Вызов пользовательской top-level функции (Phase 9, задача 9.4).
     *
     * IR: `IrCall` с `origin == null` (или PERC/PLUSEQ-производные, которые уже
     * обработаны выше), `symbol.owner` — `IrSimpleFunction`, объявленная в
     * том же файле (или другом). `dispatchReceiverParameter == null`.
     *
     * IL: `call <returnType> <namespace>.<FileKt>::<method>(<paramTypes>)`.
     * Аргументы эмитятся в порядке. Рекурсия работает автоматически
     * (статический `call` — ссылка вперёд допустима в IL).
     *
     * Класс-контейнер и namespace берутся через [NameMapper] из файла-владельца
     * функции ([IrSimpleFunction].parent → [IrFile]).
     */
    private fun emitUserTopLevelCall(call: IrCall, data: Nothing?) {
        val owner = call.symbol.owner as IrSimpleFunction
        val file = owner.parent as? IrFile
            ?: error("User function ${owner.name} is not top-level (parent is not IrFile)")
        val ns = NameMapper.namespace(file)
        val cls = NameMapper.containerClass(file)
        val methodName = NameMapper.methodName(owner)
        val retCil = TypeMapper.mapType(owner.returnType)
        val paramCilTypes = owner.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .map { TypeMapper.mapType(it.type) }
        // Внимание: аргументы уже эмитированы в [emitCall] перед вызовом
        // этого метода (через emitCallArguments). Здесь только формируем
        // сигнатуру и эмитим `call`. Если предусловие нарушено — на стеке
        // будет двойной набор аргументов.
        val container = if (ns.isEmpty()) cls else "$ns.$cls"
        val paramSig = paramCilTypes.joinToString(", ")
        val callSig = "$retCil $container::$methodName($paramSig)"
        emitter.opcode(IlOpcode.CALL, callSig)
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
                // У конструкторов нет this-параметра в IR, но в IL arg0 =
                // this, поэтому регулярные параметры сдвинуты на +1.
                // У instance-методов DispatchReceiver уже учтён в индексах.
                val shift = if (owner.parent is IrConstructor) 1 else 0
                emitter.ldarg(owner.indexInParameters + shift)
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

    // =====================================================================
    // Циклы (Phase 9: while / do-while / break / continue).
    // =====================================================================

    /**
     * `while (cond) { body }` → IL:
     * ```
     * br IL_COND          // войти в проверку условия
     * IL_BODY:
     *   <body>            // statement-context (value-expr → pop)
     *   br IL_COND
     * IL_COND:
     *   <condition>       // bool на стеке
     *   brtrue IL_BODY
     * IL_END:
     * ```
     *
     * `continue` → `br IL_COND` (проверка условия следующей итерации).
     * `break`    → `br IL_END`.
     */
    private fun emitWhileLoop(loop: IrWhileLoop, data: Nothing?) {
        val bodyLabel = emitter.newLabel()
        val condLabel = emitter.newLabel()
        val endLabel = emitter.newLabel()
        loopContexts.addLast(LoopContext(continueLabel = condLabel, breakLabel = endLabel, loop = loop))
        try {
            emitter.br(condLabel)
            emitter.label(bodyLabel)
            loop.body?.let { emitStatement(it as IrElement, data) }
            emitter.br(condLabel)
            emitter.label(condLabel)
            emitExpr(loop.condition, data)
            emitter.brtrue(bodyLabel)
            emitter.label(endLabel)
        } finally {
            loopContexts.removeLast()
        }
    }

    /**
     * `do { body } while (cond)` → IL:
     * ```
     * IL_BODY:
     *   <body>
     *   <condition>
     *   brtrue IL_BODY
     * IL_END:
     * ```
     *
     * `continue` → `br IL_BODY` (тело следующей итерации).
     * `break`    → `br IL_END`.
     */
    private fun emitDoWhileLoop(loop: IrDoWhileLoop, data: Nothing?) {
        val bodyLabel = emitter.newLabel()
        val endLabel = emitter.newLabel()
        loopContexts.addLast(LoopContext(continueLabel = bodyLabel, breakLabel = endLabel, loop = loop))
        try {
            emitter.label(bodyLabel)
            loop.body?.let { emitStatement(it as IrElement, data) }
            emitExpr(loop.condition, data)
            emitter.brtrue(bodyLabel)
            emitter.label(endLabel)
        } finally {
            loopContexts.removeLast()
        }
    }

    /**
     * `break` / `continue` (Phase 9, задача 9.3).
     *
     * Unlabeled: верхний [LoopContext] стека.
     * Labeled: поиск по `loop.label` — TODO (Phase 9.x, требует метки на
     * `IrWhileLoop.label`). Пока — unlabeled.
     *
     * `break`    → `br <breakLabel>`.
     * `continue` → `br <continueLabel>`.
     */
    private fun emitBreakContinue(loop: IrLoop, isBreak: Boolean, label: String?) {
        val ctx = if (label == null) {
            loopContexts.lastOrNull()
                ?: error("break/continue outside of a loop")
        } else {
            // Labeled: ищем контекст с совпадающей меткой цикла.
            loopContexts.lastOrNull { it.loop.label == label }
                ?: TODO("[B-01] labeled break/continue (label=$label) — Phase 9.x")
        }
        val target = if (isBreak) ctx.breakLabel else ctx.continueLabel
        emitter.br(target)
    }

    // =====================================================================
    // IrTypeOperatorCall (Phase 9: IMPLICIT_COERCION_TO_UNIT).
    // =====================================================================

    /**
     * Обработка [IrTypeOperatorCall]. В Phase 9 нужен только
     * [IrTypeOperator.IMPLICIT_COERCION_TO_UNIT] — обёртка, которая означает
     * «вычислить expression для побочного эффекта, результат отбросить».
     * Встречается в телах циклов (`x++` как statement).
     *
     * IL: эмитить argument, затем `pop` если argument не Unit.
     *
     * Остальные операторы (`CAST`, `INSTANCEOF`, …) — POST-9.
     */
    private fun emitTypeOperator(op: IrTypeOperatorCall, data: Nothing?) {
        when (op.operator) {
            IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> {
                emitExpr(op.argument, data)
                // argument может оставить значение на стеке (напр. результат
                // POSTFIX_INCR = int32). Unit уже не оставляет ничего, но
                // IR-трансформер оборачивает в coercion именно для не-Unit.
                if (!op.argument.type.isUnit()) {
                    emitter.pop()
                }
            }
            IrTypeOperator.IMPLICIT_CAST,
            IrTypeOperator.IMPLICIT_NOTNULL,
            IrTypeOperator.CAST -> TODO("[B-01] IrTypeOperator ${op.operator} — Phase 12 (casts)")
            IrTypeOperator.INSTANCEOF,
            IrTypeOperator.NOT_INSTANCEOF -> TODO("[B-01] IrTypeOperator ${op.operator} — POST-9 (is-check)")
            else -> TODO("[B-01] IrTypeOperator ${op.operator} — POST-9")
        }
    }

    // =====================================================================
    // IrStringConcatenation (Phase 9, задача 9.5).
    // =====================================================================

    /**
     * Строковая интерполяция `"$x ${expr}"` → `System.String.Concat`.
     *
     * IR: [IrStringConcatenation] с `.arguments` (List<IrExpression>) — каждый
     * child вычисляется в строку. Котлиновский lowering уже разбил шаблон на
     * сегменты (const-string + get-var + …).
     *
     * IL (простой, универсальный): эмитить каждый argument, box в `object`
     * если тип не `string`, затем `call string [mscorlib]System.String::Concat(object[])`.
     * Массив создаётся через `newarr object` + `stelem.ref`.
     *
     * Оптимизация для 2 аргументов (частый случай `"prefix" + var`):
     * `call string [mscorlib]System.String::Concat(object, object)`.
     */
    private fun emitStringConcatenation(expr: IrStringConcatenation, data: Nothing?) {
        val args = expr.arguments
        if (args.isEmpty()) {
            emitter.ldstr("")
            return
        }
        // Оптимизация: для 1-2 аргументов — перегрузки Concat(object) /
        // Concat(object,object) без создания массива. Частый случай
        // ("prefix" + var) — как в probe_concat ("x = " + x).
        if (args.size <= 2) {
            for (arg in args) {
                emitExpr(arg, data)
                boxToObject(arg.type)
            }
            val sig = if (args.size == 1) {
                "string [mscorlib]System.String::Concat(object)"
            } else {
                "string [mscorlib]System.String::Concat(object,object)"
            }
            emitter.opcode(IlOpcode.CALL, sig)
            return
        }
        // Универсальный путь: создать object[n], заполнить по индексам,
        // вызвать Concat(object[]).
        // IL для каждого i (0..n-1):
        //   dup            // копия array (сохранить для следующей итерации / вызова)
        //   ldc.i4 i       // индекс
        //   <emit arg_i>
        //   box (если value type)
        //   stelem.ref     // потребляет array+index+value (копию)
        // stelem.ref съедает копию (dup), оставляя исходный array на стеке.
        // После цикла на стеке остаётся один array → передаём в Concat(object[]).
        val n = args.size
        emitter.ldcI4(n)
        emitter.newarr("object")
        for (i in 0 until n) {
            emitter.dup()
            emitter.ldcI4(i)
            emitExpr(args[i], data)
            boxToObject(args[i].type)
            emitter.stelemRef()
        }
        emitter.opcode(
            IlOpcode.CALL,
            "string [mscorlib]System.String::Concat(object[])"
        )
    }

    /** Box'ит значение типа [type] (выражение уже на стеке) в object. */
    private fun boxToObject(type: org.jetbrains.kotlin.ir.types.IrType) {
        val cilType = TypeMapper.mapType(type)
        if (cilType != "string" && cilType != "object") {
            emitter.box(cilType)
        }
    }
}
