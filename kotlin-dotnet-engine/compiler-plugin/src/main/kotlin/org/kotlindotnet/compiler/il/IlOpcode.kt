package org.kotlindotnet.compiler.il

/**
 * Перечисление всех IL-опкодов, используемых компилятором (PoC).
 *
 * **Источник правды (A-04):** единственное место, где опкод
 * связывается со своим IL-текстовым представлением. Весь кодогенератор
 * работает с [IlOpcode], а [TextIlEmitter] преобразует его в текст.
 *
 * **Не описывает все CIL-опкоды** — только то, что реально
 * эмитит `DotnetIrVisitor`. Расширяется по мере появления новых
 * (Phase 9+). Так `when (IlOpcode.X)` остаётся исчерпывающим.
 *
 * **Короткие формы** (`ldc.i4.0`...`ldc.i4.8`, `ldarg.0`...`ldarg.3`,
 * `ldloc.0`...`ldloc.3`, `stloc.0`...`stloc.3`) **не** выносятся сюда
 * как отдельные константы — они инкапсулированы в [TextIlEmitter]
 * (`ldcI4(0)` → `ldc.i4.0`, `ldarg(0)` → `ldarg.0` и т.д.). Visitor
 * не должен знать про них. Перечисление хранит только «длинные»
 * формы (с операндом), а выбор короткой формы — реализация эмиттера.
 *
 * TODO(BinaryIlEmitter): `IlOpcode → байт` + сериализация операндов.
 *
 * @property il Текстовое представление опкода в CIL assembly language.
 */
enum class IlOpcode(val il: String) {
    // === Арифметика ===
    ADD("add"),
    SUB("sub"),
    MUL("mul"),
    DIV("div"),
    REM("rem"),
    NEG("neg"),

    // === Логические/битовые ===
    AND("and"),
    OR("or"),
    XOR("xor"),

    // === Сдвиги ===
    SHL("shl"),
    SHR("shr"),
    SHR_UN("shr.un"),

    // === Сравнения ===
    CEQ("ceq"),
    CGT("cgt"),
    CLT("clt"),

    // === Управление потоком ===
    RET("ret"),
    BR("br"),
    BRTRUE("brtrue"),
    BRFALSE("brfalse"),

    // === Вызовы ===
    CALL("call"),
    CALLVIRT("callvirt"),
    NEWOBJ("newobj"),

    // === Загрузка констант (без операнда) ===
    LDNULL("ldnull"),
    LDSTR("ldstr"),

    // === Загрузка констант (с операндом) ===
    /** `ldc.i4 <int>` — короткие формы 0..8 и M1 выбирает [TextIlEmitter.ldcI4]. */
    LDC_I4("ldc.i4"),
    /** `ldc.i8 <long>`. */
    LDC_I8("ldc.i8"),
    /** `ldc.r4 <float>`. */
    LDC_R4("ldc.r4"),
    /** `ldc.r8 <double>`. */
    LDC_R8("ldc.r8"),

    // === Загрузка/сохранение (с операндом-индексом) ===
    /** `ldarg <n>` — короткие формы 0..3 выбирает [TextIlEmitter.ldarg]. */
    LDARG("ldarg"),
    /** `ldloc <n>` — короткие формы 0..3 выбирает [TextIlEmitter.ldloc]. */
    LDLOC("ldloc"),
    /** `stloc <n>` — короткие формы 0..3 выбирает [TextIlEmitter.stloc]. */
    STLOC("stloc"),

    // === Box / unbox / discard ===
    /** `box <type>` — boxing value type в object (используется в string.Concat). */
    BOX("box"),
    /** `pop` — discard значения на вершине стека (statement-context для value-expr). */
    POP("pop"),
    /** `dup` — копия значения на вершине стека (для заполнения массива в string.Concat). */
    DUP("dup"),

    // === Массивы (Phase 9: string.Concat(object[])) ===
    /** `newarr <type>` — создание массива элементов типа. */
    NEWARR("newarr"),
    /** `stelem.ref` — сохранение object-ссылки в массив по индексу на стеке. */
    STELEM_REF("stelem.ref"),
    /** `ldelem.ref` — загрузка object-ссылки из массива по индексу. */
    LDELEM_REF("ldelem.ref"),
    ;
}
