package org.kotlindotnet.compiler.il

/**
 * Абстракция IL-эмиттера.
 *
 * Контракт «IL корректен»: реализации гарантируют, что
 * `beginX`/`endX` парны, тело сбалансировано, заголовок есть.
 * `opcode`/`ldcI4`/`ldarg`/.../`label`/`declareLocal` разрешены
 * только внутри метода (`beginMethod` ... `endMethod`).
 *
 * Явные `begin/end` пары вместо лямбд `IlEmitter.() -> Unit`
 * (ADR 0005 — `this` в лямбде перекрывал бы `this@DotnetIrVisitor`).
 *
 * **A-04:** опкоды типизированы через [IlOpcode]. Общий метод
 * [opcode] покрывает опкод + строковые операнды; типизированные
 * хелперы ([ldcI4], [ldarg], [ldloc], [stloc], [ldstr], [br], ...)
 * инкапсулируют выбор коротких форм — visitor
 * не знает про `ldc.i4.0` vs `ldc.i4 0`.
 */
interface IlEmitter {

    // === Assembly / module ===

    /** Эмитит заголовок сборки (.assembly extern, .assembly, .module). */
    fun assemblyHeader(assemblyName: String, withRuntime: Boolean = false)

    //=== Container class (top-level functions) ===

    /** Открывает класс-контейнер для top-level функций. */
    fun beginContainerClass(namespace: String, className: String)

    /** Закрывает класс-контейнер (+ эмитит default .ctor). */
    fun endContainerClass()

    //=== User classes (Phase 10, D2) ===

    /**
     * Открывает пользовательский класс.
     *
     * @param namespace namespace класса (verbatim из пакета).
     * @param name имя класса verbatim ([NameMapper.className]).
     * @param flags TypeAttributes (Public|Sealed/Abstract|Interface|BeforeFieldInit...).
     * @param baseTypeCil CIL-тип базового класса (`probe.p1.Point`, `object`);
     *        null → object. Для интерфейсов игнорируется (extends = nil).
     * @param interfaces CIL-имена реализуемых интерфейсов.
     */
    fun beginClass(
        namespace: String,
        name: String,
        flags: UInt,
        baseTypeCil: String?,
        interfaces: List<String> = emptyList(),
    )

    /** Закрывает пользовательский класс (фиксирует диапазоны fieldList/methodList). */
    fun endClass()

    /**
     */
    fun declareField(cilType: String, name: String, isStatic: Boolean): Int

    //=== Method ===

    /**
     * Открывает метод. Унификация static/instance (Phase 10):
     * `isStatic = true` — статический метод; `isStatic = false`
     * объявляет instance-метод (arg0 = this, параметры с индекса 1).
     *
     * @param params пары `cilType to paramName` — только Regular-параметры
     *        (receiver не входит).
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

    /** Открывает конструктор (specialname rtspecialname `.ctor`). */
    fun beginConstructor(params: List<Pair<String, String>>)

    /** Эмитит `.locals init (...)` если есть зарегистрированные локалки. */
    fun emitLocalsIfAny()

    /** Закрывает метод. */
    fun endMethod()

    /** Сбрасывает состояние локалок/меток между методами. */
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

    /** Генерирует уникальную метку IL_<n>. */
    fun newLabel(): String

    /** Эмитит метку `name:` на текущем отступе. */
    fun label(name: String)

    //=== Instructions (A-04) ===

    /**
     * Эмитит опкод [op] с операндами [operands] (через пробел).
     *
     * Пример: `opcode(IlOpcode.BR, "IL_0")` → `br IL_0`.
     * Для опкодов без операндов: `opcode(IlOpcode.ADD)` → `add`.
     *
     * Контракт: только внутри [beginMethod] ... [endMethod].
     * Короткие формы НЕ выбираются здесь — для типизированных
     * операций (ldc/ldarg/ldloc/stloc) используйте хелперы ниже.
     */
    fun opcode(op: IlOpcode, vararg operands: String)

    // --- Загрузка констант ---

    /** `ldc.i4 <value>` с короткими формами 0..8 и M1 для -1. */
    fun ldcI4(value: Int)

    /** `ldc.i8 <value>` (малые 0..8 и -1 переиспользуют ldc.i4-формы). */
    fun ldcI8(value: Long)

    /** `ldc.r4 <value>`. */
    fun ldcR4(value: Float)

    /** `ldc.r8 <value>`. */
    fun ldcR8(value: Double)

    // --- Загрузка/сохранение ---

    /** `ldarg <index>` с короткими формами 0..3. */
    fun ldarg(index: Int)

    /** `ldloc <index>` с короткими формами 0..3. */
    fun ldloc(index: Int)

    /** `stloc <index>` с короткими формами 0..3. */
    fun stloc(index: Int)

    /** `ldstr "<escaped>"` с экранированием CIL. */
    fun ldstr(s: String)

    // --- Управление потоком (типизированные по метке) ---

    /** `br <label>`. */
    fun br(label: String)

    /** `brtrue <label>`. */
    fun brtrue(label: String)

    /** `brfalse <label>`. */
    fun brfalse(label: String)

    // === Box / discard / массивы ===

    /** `box <cilType>` (для value type → object). */
    fun box(cilType: String)

    /** `pop` — discard значения на вершине стека. */
    fun pop()

    /** `dup` — копия значения на вершине стека. */
    fun dup()

    /** `newarr <cilType>` — создать массив. */
    fun newarr(cilType: String)

    /** `stelem.ref` — сохранить object в массив по индексу. */
    fun stelemRef()

    //=== Result ===
}
