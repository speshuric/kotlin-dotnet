package org.kotlindotnet.compiler

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrElementBase
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
 * Visitor over the IR tree that emits IL.
 *
 * PoC Phases 7–9 cover:
 * - `IrFile → IrSimpleFunction(top-level) → IrBlockBody`
 * - `IrReturn`, `IrCall` (operators + comparisons + stdlib + user top-level),
 *   `IrGetValue`, `IrConst`
 * - `IrVariable` (local variables, `val`/`var`)
 * - `IrSetValue` (assignment to a local)
 * - `IrWhen` + `IrBranch` (if/when/ANDAND/OROR)
 * - `IrBlock` (expression block), `IrComposite`
 * - **Phase 9:** `IrWhileLoop`, `IrDoWhileLoop`, `IrBreak`, `IrContinue`
 *   (the LoopContext stack), `IrStringConcatenation` (`string.Concat`),
 *   `IrTypeOperatorCall` (`IMPLICIT_COERCION_TO_UNIT` — evaluate and discard),
 *   operators `inc`/`dec` (→ `±1` for primitives), calls to user-defined
 *   top-level functions.
 *
 * B-01: added scaffolding — an `override fun visitXxx` for each node
 * from the map (`TODO/B-visitor-scaffolding/B-01-visitor-node-map.md`).
 * Already-implemented nodes delegate to private `emitXxx`; unimplemented
 * ones throw `TODO("[B-01] <node> — Phase N")` naming the phase.
 *
 * Returns Unit: IlEmitter accumulates state internally.
 *
 * The call context is tracked via [MethodContext] (a stack of methods);
 * the loop context (break/continue) — via [loopContexts] (a stack of
 * [LoopContext]).
 *
 * Depends on the [IlEmitter] abstraction. Opcodes are emitted through
 * type-safe methods: `emitter.opcode(IlOpcode.ADD)` for operand-free ones,
 * `emitter.ldcI4(n)`, `emitter.ldarg(idx)`, `emitter.br(label)` for typed
 * ones (short forms are encapsulated in the emitter implementation).
 */
class DotnetIrVisitor(
    private val emitter: IlEmitter,
    private val uncoveredStdlib: UncoveredStdlibCollector = UncoveredStdlibCollector(),
) : IrVisitor<Unit, Nothing?>() {

    /** Local variable indices keyed by their symbol (within the current method). */
    private val variableIndices = mutableMapOf<IrVariable, Int>()

    /** Line map of the current file, used for sequence points. */
    private var fileEntry: org.jetbrains.kotlin.ir.IrFileEntry? = null

    /**
     * Stack of loop contexts for break/continue (Phase 9).
     *
     * `continueLabel` is the label `continue` jumps to (start of the next
     * iteration: the condition check for while, the body for do-while).
     * `breakLabel` is the loop exit label.
     * `loop` is the [IrLoop] symbol so that [IrBreak]/[IrContinue] find their
     * context (labeled break/continue is Phase 9.x; unlabeled only for now).
     */
    private data class LoopContext(
        val continueLabel: String,
        val breakLabel: String,
        val loop: IrLoop,
    )

    private val loopContexts = ArrayDeque<LoopContext>()

    // =====================================================================
    // Fallback (last resort — completely unknown nodes).
    // After B-01 almost every node has a specific visitXxx, so only truly
    // exotic elements reach here.
    // =====================================================================

    override fun visitElement(element: IrElement, data: Nothing?) {
        TODO("IR element not supported in PoC: ${element::class.simpleName}")
    }

    // =====================================================================
    // Declarations (IrDeclaration)
    // =====================================================================

    override fun visitFile(declaration: IrFile, data: Nothing?) {
        fileEntry = declaration.fileEntry
        val ns = NameMapper.namespace(declaration)
        val cls = NameMapper.containerClass(declaration)
        val asm = NameMapper.assemblyName(declaration)

        // Always emit the extern for KotlinDotnetRuntime — a redundant extern
        // does no harm, while a two-pass scan for stdlib calls would complicate the PoC.
        emitter.assemblyHeader(asm, withRuntime = true)

        // Two-pass order (Phase 10): first all top-level functions into the
        // container class, then user classes. This keeps the MethodDef rows of
        // each group of types contiguous (an ECMA requirement for the
        // fieldList/methodList ranges). Finalization (pe backend) happens in
        // endContainerClass — classes register BEFORE it.
        emitter.beginContainerClass(ns, cls)
        for (decl in declaration.declarations) {
            if (decl !is IrSimpleFunction) continue
            if (decl.dispatchReceiverParameter != null) continue // class members go in the second pass
            emitSimpleFunction(decl, data)
        }
        for (decl in declaration.declarations) {
            if (decl is IrClass) emitClass(decl, data)
        }
        emitter.endContainerClass()
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?) {
        // Top-level (Phase 4) and instance methods/accessors (Phase 10):
        // distinguished inside emitSimpleFunction by dispatchReceiverParameter.
        emitSimpleFunction(declaration, data)
    }

    override fun visitClass(declaration: IrClass, data: Nothing?) {
        emitClass(declaration, data)
    }

    override fun visitConstructor(declaration: IrConstructor, data: Nothing?) {
        emitConstructor(declaration, data)
    }

    override fun visitField(declaration: IrField, data: Nothing?) {
        // Fields are declared in emitClass (declareField); IrField gets no
        // standalone traversal — initializers are injected into the primary ctor.
    }

    override fun visitProperty(declaration: IrProperty, data: Nothing?) {
        // Properties are handled in emitClass: backing field → declareField,
        // default accessors → plain instance methods (observations 10.0).
    }

    override fun visitVariable(declaration: IrVariable, data: Nothing?) {
        emitVariable(declaration, data)
    }

    override fun visitValueParameter(declaration: IrValueParameter, data: Nothing?) {
        // Handled in emitSimpleFunction (collecting method parameters) and
        // in emitGetValue (ldarg by index). No standalone traversal needed.
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
        // Empty init blocks are a no-op (observations 10.0: K2 puts them into
        // the primary ctor; property initializers are injected separately).
        val hasStatements = (declaration.body as? IrBlockBody)?.statements?.isNotEmpty() == true
        if (hasStatements) {
            TODO("[B-01] IrAnonymousInitializer (non-empty) — Phase 10.x")
        }
    }

    // Note: IrEnumDeclaration in the Kotlin IR tree is an IrClass carrying the
    // enum flag (see IrClass.hasEnumEntries); there is no separate node.
    // It reaches here through visitClass (Phase 13).

    override fun visitScript(declaration: IrScript, data: Nothing?) {
        TODO("[B-01] IrScript — POST-PoC")
    }

    // =====================================================================
    // Bodies (IrBody)
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
    // Expressions (IrExpression) — the bulk of the scaffolding.
    // Already-implemented nodes (Phase 7/8) delegate to emitXxx.
    // =====================================================================

    override fun visitConst(expression: IrConst, data: Nothing?) {
        emitConst(expression)
    }

    override fun visitCall(expression: IrCall, data: Nothing?) {
        // operator-call (Phase 7) and stdlib-call (Phase 8) are implemented;
        // user top-level / instance / extension — TODO inside emitCall.
        emitCall(expression, data)
    }

    override fun visitGetValue(expression: IrGetValue, data: Nothing?) {
        emitGetValue(expression)
    }

    override fun visitSetValue(expression: IrSetValue, data: Nothing?) {
        // locals (Phase 7) are implemented; fields (Phase 10) — TODO inside emitSetValue.
        emitSetValue(expression, data)
    }

    override fun visitReturn(expression: IrReturn, data: Nothing?) {
        emitReturn(expression, data)
    }

    override fun visitWhen(expression: IrWhen, data: Nothing?) {
        // if/when as statement or expression — dispatched in emitStatement/emitExpr.
        // Direct traversal rarely lands here, but the framework may call it.
        emitWhenDispatch(expression, data)
    }

    override fun visitBranch(branch: IrBranch, data: Nothing?) {
        // Part of IrWhen — handled together with it.
        TODO("[B-01] IrBranch — handled within IrWhen (Phase 7)")
    }

    override fun visitBlock(expression: IrBlock, data: Nothing?) {
        // for-loop lowering arrives as an IrBlock with origin=FOR_LOOP (see the dump).
        // Phase 9 does not implement for (requires iterators — Phase 11 / G-02).
        if (expression.origin?.debugName == "FOR_LOOP") {
            TODO("[B-01] for-loop — Phase 11/G-02 (requires iterators)")
        }
        for (stmt in expression.statements) emitStatement(stmt, data)
    }

    override fun visitComposite(expression: IrComposite, data: Nothing?) {
        // IrComposite is a sequence of expressions whose result is the last one.
        // In statement context (loops, blocks) we emit all statements;
        // the value of the last is discarded by the caller (popped when needed).
        for (stmt in expression.statements) emitStatement(stmt, data)
    }

    // --- Expressions — per-phase TODO stubs ---

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
        // Instance-initialization marker in the primary ctor (observations 10.0):
        // a no-op for us — allocation happens at the newobj call site.
    }

    // Note: IrNewInstance in the Kotlin IR tree is an IrConstructorCall
    // (visitConstructorCall); there is no separate IrNewInstance node.
    override fun visitConstructorCall(expression: IrConstructorCall, data: Nothing?) {
        emitConstructorCall(expression, data)
    }

    override fun visitDelegatingConstructorCall(
        expression: IrDelegatingConstructorCall,
        data: Nothing?,
    ) {
        emitDelegatingConstructorCall(expression, data)
    }

    // Note: the Kotlin IR tree has no IrNewArray — arrays are created via
    // IrVararg (Phase 11) or IrConstantArray. See the node map.

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
    // Private emit-methods (implementation of already-handled nodes).
    // =====================================================================

    private fun emitSimpleFunction(
        declaration: IrSimpleFunction,
        data: Nothing?,
        attributesOverride: UInt? = null,
    ) {
        val isInstance = declaration.dispatchReceiverParameter != null

        // Reset state for the new method.
        variableIndices.clear()
        emitter.resetMethodState()

        val name = NameMapper.methodName(declaration)
        val retType = cilTypeOf(declaration.returnType)
        val params = declaration.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .map { p: IrValueParameter ->
                cilTypeOf(p.type) to p.name.asString()
            }

        // main() → .entrypoint (Phase 8); static main only (Phase 10).
        val isEntrypoint = !isInstance && name == "main"
        emitter.beginMethod(
            name, retType, params,
            isStatic = !isInstance,
            isEntrypoint = isEntrypoint,
            attributesOverride = attributesOverride,
        )

        // Walk the body first to collect local variables so that
        // .locals init precedes the instructions.
        val body = declaration.body
            ?: error("Function $name has no body")
        collectLocals(body)
        emitter.emitLocalsIfAny()

        emitBody(body, data)

        // For void methods with no explicit ret, add one.
        if (retType == "void") {
            emitter.opcode(IlOpcode.RET)
        }

        emitter.endMethod()
    }

    // =====================================================================
    // Classes (Phase 10): emitClass / constructors / fields / instance calls.
    // Actual IR shapes are in TODO/PHASE-10.md §"10.0 Observations".
    // =====================================================================

    /**
     * User class → beginClass + fields + constructors/methods.
     *
     * FAKE_OVERRIDE members (equals/hashCode/toString and inherited ones)
     * are skipped — they have no bodies; real implementations live in the ancestor.
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

        // Property backing fields + their default accessors (observations 10.0:
        // FIELD PROPERTY_BACKING_FIELD; accessors hang off the property and
        // are absent from class.declarations).
        for (prop in declaration.declarations.filterIsInstance<IrProperty>()) {
            if (prop.origin == IrDeclarationOrigin.FAKE_OVERRIDE) continue
            val backingField = prop.backingField
            if (backingField != null) {
                emitter.declareField(cilTypeOf(backingField.type), backingField.name.asString(), backingField.isStatic)
            }
            // Default accessors are emitted as plain instance methods.
            for (accessor in listOf(prop.getter, prop.setter)) {
                if (accessor == null) continue
                if (accessor.body != null && accessor.origin != IrDeclarationOrigin.FAKE_OVERRIDE) {
                    emitSimpleFunction(accessor, data, memberAttributes(accessor, declaration))
                }
            }
        }
        // Direct field declarations (without a property), just in case.
        for (field in declaration.declarations.filterIsInstance<IrField>()) {
            emitter.declareField(cilTypeOf(field.type), field.name.asString(), field.isStatic)
        }

        for (member in declaration.declarations) {
            when (member) {
                is IrConstructor -> emitConstructor(member, data)
                is IrSimpleFunction ->
                    // FAKE_OVERRIDEs have no bodies; default accessors and regular
                    // methods go through the single instance path.
                    if (member.body != null && member.origin != IrDeclarationOrigin.FAKE_OVERRIDE) {
                        emitSimpleFunction(member, data, memberAttributes(member, declaration))
                    }
                else -> Unit
            }
        }

        emitter.endClass()
    }

    /** Empty interface → TypeDef with Interface|Abstract; members with bodies — Phase 13. */
    private fun emitInterface(declaration: IrClass) {
        val file = declaration.parent as? IrFile
            ?: error("Interface ${declaration.name} is not top-level")
        val ns = NameMapper.namespace(file)
        // Public | Interface | Abstract (TypeAttributes.Public = 0x1!);
        // extends = nil (see observation P4).
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
     * Class constructor. The body already contains the DELEGATING_CONSTRUCTOR_CALL
     * (+ INSTANCE_INITIALIZER_CALL — a no-op). After the delegating call we inject
     * backing-field initializers (observations 10.0: they hang on
     * FIELD.EXPRESSION_BODY and are NOT present in the ctor body).
     *
     * Injection goes only into the primary ctor: secondary ctors delegate to
     * this(...), so initialization runs exactly once along the chain.
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
     * `Point(1, 2)` → args + NEWOBJ `.ctor`. There is no receiver slot
     * (observations 10.0): newobj performs the allocation itself.
     */
    private fun emitConstructorCall(call: IrConstructorCall, data: Nothing?) {
        call.arguments.forEach { a -> a?.let { emitExpr(it, data) } }
        emitter.opcode(IlOpcode.NEWOBJ, constructorRef(call.symbol.owner))
    }

    /**
     * Delegating constructor call (base or this). No receiver slot either —
     * `this` is supplied explicitly via ldarg.0 before the arguments.
     */
    private fun emitDelegatingConstructorCall(
        call: IrDelegatingConstructorCall,
        data: Nothing?,
    ) {
        emitter.ldarg(0)
        call.arguments.forEach { a -> a?.let { emitExpr(it, data) } }
        emitter.opcode(IlOpcode.CALL, constructorRef(call.symbol.owner))
    }

    /** `ldfld <field-ref>`: the receiver comes from [expression.receiver] on the stack. */
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

    /** `stfld <field-ref>`: stack order — object, value. */
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
     * Instance call → `callvirt`. The dispatch receiver occupies `arguments[0]`
     * (observations 10.0). The ref is built from the declaring class of the real
     * implementation: fake overrides (bodyless) walk down overriddenSymbols
     * (observation P3: reading an inherited property resolves to a fake override
     * of the derived class).
     */
    private fun emitInstanceCall(call: IrCall, data: Nothing?) {
        val callee = call.symbol.owner as IrSimpleFunction
        val realCallee = resolveRealOverride(callee)

        val dispatch = call.dispatchReceiver
            ?: error("Instance call without dispatch receiver: ${callee.name}")
        emitExpr(dispatch, data)

        // Remaining argument slots after the dispatch receiver.
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

    /** Walk overriddenSymbols down to a function with a body (fake overrides have none). */
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
     * MethodAttributes for open/override (10.7).
     *
     * override → Public|HideBySig|Virtual (ReuseSlot, 0x00C6) — MUST be
     * virtual even in a final class, otherwise the method hides the base slot
     * and polymorphism through a base-typed variable breaks;
     * open in a class not open for inheritance → default (nobody can override);
     * open in an open class → + NewSlot (0x01C6).
     */
    private fun memberAttributes(fn: IrSimpleFunction, containingClass: IrClass): UInt? =
        when {
            fn.overriddenSymbols.isNotEmpty() -> 0x000000C6u
            containingClass.modality != Modality.FINAL && fn.modality == Modality.OPEN -> 0x000001C6u
            else -> null
        }

    // === Class helpers ===

    /**
     * TypeAttributes. IMPORTANT: Public for types = 0x00000001 (visibility
     * mask 0x7); do not confuse with MethodAttributes.Public = 0x6.
     * final → Public|Sealed|BeforeFieldInit, open → Public|BeforeFieldInit.
     */
    private fun classFlags(declaration: IrClass): UInt =
        when (declaration.modality) {
            Modality.FINAL -> 0x00100101u // Public | Sealed | BeforeFieldInit
            Modality.OPEN -> 0x00100001u // Public | BeforeFieldInit
            else -> TODO("[B-01] class modality ${declaration.modality} — Phase 13")
        }

    /** CIL type: primitives via TypeMapper, user classes as the fqName verbatim. */
    private fun cilTypeOf(type: IrType): String {
        val mapped = TypeMapper.mapType(type)
        if (mapped != "object") return mapped
        // "object" is kotlin.Any/Any? or a user-defined class.
        val owner = type.classOrNull?.owner as? IrClass ?: return "object"
        val fqName = owner.kotlinFqName.asString()
        return if (fqName.startsWith("kotlin.") || fqName.isEmpty()) "object" else fqName
    }

    /** Full CIL class name (`probe.p1.Point`). */
    private fun qualifiedClassName(declaration: IrClass): String =
        declaration.kotlinFqName.asString()

    /** Field-ref for ldfld/stfld: `<cilType> <Ns.Class>::<field>`. */
    private fun fieldRef(field: IrField): String {
        val owner = field.parent as? IrClass
            ?: error("Field ${field.name} has no IrClass parent")
        return "${cilTypeOf(field.type)} ${qualifiedClassName(owner)}::${field.name.asString()}"
    }

    /** Ctor-ref for call/newobj: `void instance <Ns.Class>::.ctor(<params>)`. */
    private fun constructorRef(constructor: IrConstructor): String {
        val owner = constructor.parent as? IrClass
            ?: error("Constructor has no IrClass parent")
        val paramSig = constructor.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .joinToString(", ") { cilTypeOf(it.type) }
        // The base ctor of kotlin.Any → BCL System.Object (outside our assembly).
        val typeName =
            if (owner.kotlinFqName.asString() == "kotlin.Any") "[System.Runtime]System.Object"
            else qualifiedClassName(owner)
        return "void instance $typeName::.ctor($paramSig)"
    }

    /** Pre-pass registering every IrVariable in .locals. */
    private fun collectLocals(element: IrElement) {
        when (element) {
            is IrVariable -> {
                val cilType = TypeMapper.mapType(element.type)
                val idx = emitter.declareLocal(cilType, element.name.asString())
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
        emitSequencePointFor(e)
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

    /**
     * Emits a sequence point for the node's source range.
     * PDB lines/columns are 1-based; SourceRangeInfo is 0-based.
     */
    private fun emitSequencePointFor(e: IrElement) {
        val fe = fileEntry ?: return
        val base = e as? IrElementBase ?: return
        if (base.startOffset < 0 || base.endOffset < 0) return
        val info = fe.getSourceRangeInfo(base.startOffset, base.endOffset)
        emitter.markSequencePoint(
            info.startLineNumber + 1,
            info.startColumnNumber + 1,
            info.endLineNumber + 1,
            info.endColumnNumber + 1,
        )
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

        // Handle special origins first (comparisons, logical ops),
        // since they have their own IL semantics.
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
                // In fact && / || become an IrWhen in Kotlin lowering
                // (see the test_bool dump). If they still arrive as an IrCall — fallback.
                TODO("ANDAND/OROR expected to be IrWhen, got IrCall")
            }
        }

        // Stdlib calls (println, print) → KotlinDotnetRuntime.
        // Detection is delegated to StdlibResolver (A-05, see D-02).
        if (StdlibResolver.isStdlibCall(call)) {
            emitStdlibCall(call, data)
            return
        }

        val symbolName = call.symbol.owner.name.asString()

        // User call (top-level Phase 9 / instance Phase 10): the matching handler
        // emits the arguments — in an instance call the dispatch receiver takes
        // slot 0 of arguments.
        //
        // Operator names are distinguished by the callee's declaration: plus/inc/...
        // are treated as IL opcodes only when the function is declared in kotlin.*
        // (methods of primitives). A user-defined `class C { fun inc() }` is a
        // plain instance call.
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

        // Regular operator-call: emit arguments, then the opcode.
        emitCallArguments(call, data)

        // Unary / binary operators.
        when (symbolName) {
            KotlinOperators.PLUS -> emitter.opcode(IlOpcode.ADD)
            KotlinOperators.MINUS -> emitter.opcode(IlOpcode.SUB)
            KotlinOperators.TIMES -> emitter.opcode(IlOpcode.MUL)
            KotlinOperators.DIV -> emitter.opcode(IlOpcode.DIV)
            KotlinOperators.REM -> emitter.opcode(IlOpcode.REM)
            KotlinOperators.UNARY_MINUS -> emitter.opcode(IlOpcode.NEG)
            KotlinOperators.UNARY_PLUS -> Unit // nop
            KotlinOperators.NOT -> {
                // For Boolean: not is logical NOT, not bitwise.
                // not(true)=1 → 0xFFFFFFFE (bitwise), but we need 0.
                // Use ldc.i4.0 + ceq (logical NOT).
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
                // a.inc() → a + 1 (for primitives). The <this> argument is already on the stack.
                emitter.ldcI4(1)
                emitter.opcode(IlOpcode.ADD)
            }
            KotlinOperators.DEC -> {
                // a.dec() → a - 1 (for primitives). The <this> argument is already on the stack.
                emitter.ldcI4(1)
                emitter.opcode(IlOpcode.SUB)
            }
            KotlinOperators.RANGE_TO -> TODO("[B-01] IrCall RANGE_TO — Phase 11 (ranges)")
            else -> {
                // Not an operator-call — unreachable here: user calls are handled
                // above (see the OPERATOR_SYMBOLS check).
                error("Unexpected call symbol '$symbolName' in operator dispatch")
            }
        }
    }

    /**
     * Call to a user-defined top-level function (Phase 9, item 9.4).
     *
     * IR: an `IrCall` with `origin == null` (or PERC/PLUSEQ derivatives, which are
     * already handled above); `symbol.owner` is an `IrSimpleFunction` declared in
     * the same file (or another one). `dispatchReceiverParameter == null`.
     *
     * IL: `call <returnType> <namespace>.<FileKt>::<method>(<paramTypes>)`.
     * Arguments are emitted in order. Recursion works automatically
     * (a static `call` allows a forward reference in IL).
     *
     * The container class and namespace come via [NameMapper] from the file owning
     * the function ([IrSimpleFunction].parent → [IrFile]).
     */
    private fun emitUserTopLevelCall(call: IrCall, data: Nothing?) {
        val owner = call.symbol.owner as IrSimpleFunction
        val file = owner.parent as? IrFile
            ?: run {
                // Not a same-file top-level function and not covered anywhere:
                // for kotlin.* callees this is exactly the uncovered case.
                // Tally it and fail here with the historical message; strict
                // mode aggregates these into one summary after the pass
                // would have completed - but emission aborts at first such
                // call, so the collector may hold only the prefix. Strict
                // policy therefore reports what reached the funnel plus this.
                if (owner.kotlinFqName.asString().startsWith("kotlin.")) {
                    uncoveredStdlib.record(owner.kotlinFqName.asString())
                    throw IllegalStateException(
                        "Stdlib call ${owner.kotlinFqName.asString()} not supported in PoC",
                    )
                }
                error("User function ${owner.name} is not top-level (parent is not IrFile)")
            }
        val ns = NameMapper.namespace(file)
        val cls = NameMapper.containerClass(file)
        val methodName = NameMapper.methodName(owner)
        val retCil = TypeMapper.mapType(owner.returnType)
        val paramCilTypes = owner.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .map { TypeMapper.mapType(it.type) }
        // Caution: arguments have already been emitted by [emitCall] before this
        // method runs (via emitCallArguments). Here we only build the signature and
        // emit the `call`. If that precondition is broken, the stack ends up with a
        // duplicate set of arguments.
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
        // Arguments: emit all non-null arguments (in order).
        call.arguments.forEach { a ->
            if (a != null) emitExpr(a, data)
        }
        // Select the overload by argument type.
        val paramCilTypes = call.arguments.mapNotNull { a ->
            a?.let { TypeMapper.mapType(it.type) }
        }
        val runtimeSig = StdlibResolver.resolveStdlibCall(call, "void", paramCilTypes)
        if (runtimeSig == null) {
            // Registered nowhere: goes to the coverage tally; strict mode
            // turns this into a compile error after emission (ADR 0014).
            // Lenient keeps the historical fail-fast TODO at this point.
            uncoveredStdlib.record(fqName)
            TODO("Stdlib call \$fqName not supported in PoC")
        }
        emitter.opcode(IlOpcode.CALL, runtimeSig)
    }

    private fun emitBinaryArgs(call: IrCall, data: Nothing?) {
        // Comparisons take two arguments (arg0, arg1).
        call.arguments.forEach { a ->
            if (a != null) emitExpr(a, data)
        }
    }

    private fun emitComparison(origin: String) {
        // IL has no direct "greater than" opcode; use cgt / clt + ceq.
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
                // Constructors have no this-parameter in IR, but in IL arg0 is
                // this, so regular parameters are shifted by +1.
                // For instance methods the DispatchReceiver is already accounted
                // for in the indices.
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
     * Dispatch an IrWhen as a statement (with RETURN) or as an expression.
     * Called from [visitWhen] when the framework traverses it directly.
     */
    private fun emitWhenDispatch(w: IrWhen, data: Nothing?) {
        // A result of unit type means statement, otherwise expression.
        if (w.type.isUnit()) {
            emitWhenAsStatement(w, data)
        } else {
            emitWhenAsExpression(w, data)
        }
    }

    private fun emitWhenAsStatement(w: IrWhen, data: Nothing?) {
        // if/when as a statement (when branches contain RETURN).
        val endLabel = emitter.newLabel()
        for (branch in w.branches) {
            val cond = branch.condition
            val isTrueConst = (cond is IrConst && cond.kind == IrConstKind.Boolean && cond.value == true)
            if (isTrueConst) {
                // else branch (condition = true): emit result, jump to end.
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
        // if/when as an expression — the result goes on the stack.
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
        // condition = a (any boolean expression); if false → result of the 2nd branch (false).
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
        // condition = a (any boolean expression); if true → result = firstBranch.result (true).
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
    // Loops (Phase 9: while / do-while / break / continue).
    // =====================================================================

    /**
     * `while (cond) { body }` → IL:
     * ```
     * br IL_COND          // enter the condition check
     * IL_BODY:
     *   <body>            // statement-context (value-expr → pop)
     *   br IL_COND
     * IL_COND:
     *   <condition>       // bool on the stack
     *   brtrue IL_BODY
     * IL_END:
     * ```
     *
     * `continue` → `br IL_COND` (the next iteration's condition check).
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
     * `continue` → `br IL_BODY` (the body of the next iteration).
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
     * `break` / `continue` (Phase 9, item 9.3).
     *
     * Unlabeled: the top [LoopContext] of the stack.
     * Labeled: search by `loop.label` — TODO (Phase 9.x; requires labels on
     * `IrWhileLoop.label`). Unlabeled only for now.
     *
     * `break`    → `br <breakLabel>`.
     * `continue` → `br <continueLabel>`.
     */
    private fun emitBreakContinue(loop: IrLoop, isBreak: Boolean, label: String?) {
        val ctx = if (label == null) {
            loopContexts.lastOrNull()
                ?: error("break/continue outside of a loop")
        } else {
            // Labeled: look for the context with a matching loop label.
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
     * Handles [IrTypeOperatorCall]. Phase 9 needs only
     * [IrTypeOperator.IMPLICIT_COERCION_TO_UNIT] — a wrapper meaning
     * "evaluate the expression for its side effect and discard the result".
     * Occurs in loop bodies (`x++` as a statement).
     *
     * IL: emit the argument, then `pop` if the argument is not Unit.
     *
     * The remaining operators (`CAST`, `INSTANCEOF`, …) are POST-9.
     */
    private fun emitTypeOperator(op: IrTypeOperatorCall, data: Nothing?) {
        when (op.operator) {
            IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> {
                emitExpr(op.argument, data)
                // The argument may leave a value on the stack (e.g. the result of
                // POSTFIX_INCR = int32). Unit already leaves nothing, but the IR
                // transformer wraps precisely non-Unit values in this coercion.
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
    // IrStringConcatenation (Phase 9, item 9.5).
    // =====================================================================

    /**
     * String interpolation `"$x ${expr}"` → `System.String.Concat`.
     *
     * IR: an [IrStringConcatenation] with `.arguments` (List<IrExpression>) — each
     * child is evaluated to a string. Kotlin lowering has already split the template
     * into segments (const-string + get-var + …).
     *
     * IL (simple and universal): emit each argument, box it into `object` if the
     * type is not `string`, then `call string [mscorlib]System.String::Concat(object[])`.
     * The array is created via `newarr object` + `stelem.ref`.
     *
     * Optimization for two arguments (the frequent `"prefix" + var` case):
     * `call string [mscorlib]System.String::Concat(object, object)`.
     */
    private fun emitStringConcatenation(expr: IrStringConcatenation, data: Nothing?) {
        val args = expr.arguments
        if (args.isEmpty()) {
            emitter.ldstr("")
            return
        }
        // Optimization: for one or two arguments use the Concat(object) /
        // Concat(object,object) overloads without building an array. Frequent case:
        // ("prefix" + var), as in probe_concat ("x = " + x).
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
        // Universal path: create an object[n], fill it by index,
        // call Concat(object[]).
        // IL for each i (0..n-1):
        //   dup            // copy of the array (kept for the next iteration / the call)
        //   ldc.i4 i       // index
        //   <emit arg_i>
        //   box (if a value type)
        //   stelem.ref     // consumes array+index+value (the copy)
        // stelem.ref eats the copy (dup), leaving the original array on the stack.
        // After the loop a single array remains on the stack → pass it to Concat(object[]).
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

    /** Boxes a value of type [type] (the expression is already on the stack) into object. */
    private fun boxToObject(type: org.jetbrains.kotlin.ir.types.IrType) {
        val cilType = TypeMapper.mapType(type)
        if (cilType != "string" && cilType != "object") {
            emitter.box(cilType)
        }
    }
}
