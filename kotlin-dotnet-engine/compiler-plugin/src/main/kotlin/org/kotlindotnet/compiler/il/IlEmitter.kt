package org.kotlindotnet.compiler.il

/**
 * Abstraction of the IL emitter.
 *
 * Contract "the IL is correct": implementations guarantee that
 * `beginX`/`endX` calls are paired, the body is balanced, and a header exists.
 * `opcode`/`ldcI4`/`ldarg`/.../`label`/`declareLocal` are allowed
 * only inside a method (`beginMethod` ... `endMethod`).
 *
 * Explicit `begin/end` pairs instead of `IlEmitter.() -> Unit` lambdas
 * (ADR 0005 — `this` inside the lambda would shadow `this@DotnetIrVisitor`).
 *
 * **A-04:** opcodes are typed via [IlOpcode]. The general-purpose
 * [opcode] method covers opcode + string operands; typed helpers
 * ([ldcI4], [ldarg], [ldloc], [stloc], [ldstr], [br], ...) encapsulate the choice
 * of short forms — the visitor never sees `ldc.i4.0` vs `ldc.i4 0`.
 */
interface IlEmitter {

    // === Assembly / module ===

    /** Emits the assembly header (.assembly extern, .assembly, .module). */
    fun assemblyHeader(assemblyName: String, withRuntime: Boolean = false)

    //=== Container class (top-level functions) ===

    /** Opens the container class for top-level functions. */
    fun beginContainerClass(namespace: String, className: String)

    /** Closes the container class (+ emits the default .ctor). */
    fun endContainerClass()

    //=== User classes (Phase 10, D2) ===

    /**
     * Opens a user-defined class.
     *
     * @param namespace the class namespace (verbatim from the package).
     * @param name the class name verbatim ([NameMapper.className]).
     * @param flags TypeAttributes (Public|Sealed/Abstract|Interface|BeforeFieldInit...).
     * @param baseTypeCil CIL type of the base class (`probe.p1.Point`, `object`);
     *        null → object. Ignored for interfaces (extends = nil).
     * @param interfaces CIL names of the implemented interfaces.
     */
    fun beginClass(
        namespace: String,
        name: String,
        flags: UInt,
        baseTypeCil: String?,
        interfaces: List<String> = emptyList(),
    )

    /** Closes the user-defined class (fixes the fieldList/methodList ranges). */
    fun endClass()

    /**
     */
    fun declareField(cilType: String, name: String, isStatic: Boolean): Int

    //=== Method ===

    /**
     * Opens a method. Static/instance unification (Phase 10):
     * `isStatic = true` — a static method; `isStatic = false`
     * declares an instance method (arg0 = this, parameters start at index 1).
     *
     * @param params pairs of `cilType to paramName` — Regular parameters only
     *        (the receiver is not included).
     * @param attributesOverride explicit MethodAttributes
     *        (Virtual/NewSlot for open/override); null → defaults
     *        (Public|HideBySig[|Static], ctor +SpecialName|RTSpecialName).
     */
    fun beginMethod(
        name: String,
        returnType: String,
        params: List<Pair<String, String>>,
        isStatic: Boolean,
        isEntrypoint: Boolean = false,
        attributesOverride: UInt? = null,
    )

    /** Opens a constructor (specialname rtspecialname `.ctor`). */
    fun beginConstructor(params: List<Pair<String, String>>)

    /** Emits `.locals init (...)` if any locals were registered. */
    fun emitLocalsIfAny()

    /** Closes the method. */
    fun endMethod()

    /** Resets local/label state between methods. */
    fun resetMethodState()

    //=== Locals / labels ===

    /**
     * Registers a local variable and returns its index.
     * [name] is the IR name used for debug tables; may be null.
     */
    fun declareLocal(cilType: String, name: String? = null): Int

    /**
     * Records a sequence point: binds the current position in the IL
     * stream to a source line range. No-op by default.
     */
    fun markSequencePoint(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) {}

    /** Generates a unique label IL_<n>. */
    fun newLabel(): String

    /** Emits the label `name:` at the current indentation level. */
    fun label(name: String)

    //=== Instructions (A-04) ===

    /**
     * Emits opcode [op] with [operands] (space-separated).
     *
     * Example: `opcode(IlOpcode.BR, "IL_0")` → `br IL_0`.
     * For operand-free opcodes: `opcode(IlOpcode.ADD)` → `add`.
     *
     * Contract: only inside [beginMethod] ... [endMethod].
     * Short forms are NOT chosen here — for typed operations
     * (ldc/ldarg/ldloc/stloc) use the helpers below.
     */
    fun opcode(op: IlOpcode, vararg operands: String)

    // --- Loading constants ---

    /** `ldc.i4 <value>` with short forms for 0..8 and M1 for -1. */
    fun ldcI4(value: Int)

    /** `ldc.i8 <value>` (small values 0..8 and -1 reuse the ldc.i4 forms). */
    fun ldcI8(value: Long)

    /** `ldc.r4 <value>`. */
    fun ldcR4(value: Float)

    /** `ldc.r8 <value>`. */
    fun ldcR8(value: Double)

    // --- Load/store ---

    /** `ldarg <index>` with short forms for 0..3. */
    fun ldarg(index: Int)

    /** `ldloc <index>` with short forms for 0..3. */
    fun ldloc(index: Int)

    /** `stloc <index>` with short forms for 0..3. */
    fun stloc(index: Int)

    /** `ldstr "<escaped>"` with CIL escaping. */
    fun ldstr(s: String)

    // --- Control flow (typed by label) ---

    /** `br <label>`. */
    fun br(label: String)

    /** `brtrue <label>`. */
    fun brtrue(label: String)

    /** `brfalse <label>`. */
    fun brfalse(label: String)

    // === Box / discard / arrays ===

    /** `box <cilType>` (a value type → object). */
    fun box(cilType: String)

    /** `pop` — discards the value on top of the stack. */
    fun pop()

    /** `dup` — duplicates the value on top of the stack. */
    fun dup()

    /** `newarr <cilType>` — creates an array. */
    fun newarr(cilType: String)

    /** `stelem.ref` — stores an object into an array by index. */
    fun stelemRef()

    //=== Result ===
}
