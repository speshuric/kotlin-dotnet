package org.kotlindotnet.compiler.il

/**
 * Enumeration of all IL opcodes used by the compiler (PoC).
 *
 * **Single source of truth (A-04):** the only place where an opcode is bound to
 * its textual IL representation. The whole code generator works with [IlOpcode];
 * the emitter implementation converts it to text.
 *
 * **Does not describe every CIL opcode** — only what `DotnetIrVisitor`
 * actually emits. Grows as new ones appear (Phase 9+), so that
 * `when (IlOpcode.X)` stays exhaustive.
 *
 * **Short forms** (`ldc.i4.0`...`ldc.i4.8`, `ldarg.0`...`ldarg.3`,
 * `ldloc.0`...`ldloc.3`, `stloc.0`...`stloc.3`) are **not** lifted here as
 * separate constants — they are encapsulated in the emitter implementation
 * (`ldcI4(0)` → `ldc.i4.0`, `ldarg(0)` → `ldarg.0`, etc.). The visitor should
 * not know about them. The enum stores only the "long" forms (with an operand);
 * picking the short form is the emitter's job.
 *
 * TODO(BinaryIlEmitter): `IlOpcode → byte` + operand serialization.
 *
 * @property il The textual representation of the opcode in CIL assembly language.
 */
enum class IlOpcode(val il: String) {
    // === Arithmetic ===
    ADD("add"),
    SUB("sub"),
    MUL("mul"),
    DIV("div"),
    REM("rem"),
    NEG("neg"),

    // === Logical/bitwise ===
    AND("and"),
    OR("or"),
    XOR("xor"),

    // === Shifts ===
    SHL("shl"),
    SHR("shr"),
    SHR_UN("shr.un"),

    // === Comparisons ===
    CEQ("ceq"),
    CGT("cgt"),
    CLT("clt"),

    // === Control flow ===
    RET("ret"),
    BR("br"),
    BRTRUE("brtrue"),
    BRFALSE("brfalse"),

    // === Calls ===
    CALL("call"),
    CALLVIRT("callvirt"),
    NEWOBJ("newobj"),

    // === Constant loads (no operand) ===
    LDNULL("ldnull"),
    LDSTR("ldstr"),

    // === Constant loads (with operand) ===
    /** `ldc.i4 <int>` — short forms 0..8 and M1 are chosen by the emitter (ldcI4). */
    LDC_I4("ldc.i4"),
    /** `ldc.i8 <long>`. */
    LDC_I8("ldc.i8"),
    /** `ldc.r4 <float>`. */
    LDC_R4("ldc.r4"),
    /** `ldc.r8 <double>`. */
    LDC_R8("ldc.r8"),

    // === Load/store (with an index operand) ===
    /** `ldarg <n>` — short forms 0..3 are chosen by the emitter (ldarg). */
    LDARG("ldarg"),
    /** `ldloc <n>` — short forms 0..3 are chosen by the emitter (ldloc). */
    LDLOC("ldloc"),
    /** `stloc <n>` — short forms 0..3 are chosen by the emitter (stloc). */
    STLOC("stloc"),

    // === Box / unbox / discard ===
    /** `box <type>` — boxes a value type into object (used in string.Concat). */
    BOX("box"),
    /** `pop` — discards the value on top of the stack (statement context for value expressions). */
    POP("pop"),
    /** `dup` — duplicates the value on top of the stack (to fill the array in string.Concat). */
    DUP("dup"),

    // === Arrays (Phase 9: string.Concat(object[])) ===
    /** `newarr <type>` — creates an array of elements of the given type. */
    NEWARR("newarr"),
    /** `stelem.ref` — stores an object reference into the array at the index on the stack. */
    STELEM_REF("stelem.ref"),
    /** `ldelem.ref` — loads an object reference from the array at the index. */
    LDELEM_REF("ldelem.ref"),

    // === Instance fields (Phase 10; ldsfld/stsfld — Phase 13, static members) ===
    /** `ldfld <field-ref>` — loads an instance field (receiver on the stack). */
    LDFLD("ldfld"),
    /** `stfld <field-ref>` — stores into an instance field (receiver+value on the stack). */
    STFLD("stfld"),
    ;
}
