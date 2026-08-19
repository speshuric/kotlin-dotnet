package org.kotlindotnet.compiler

/**
 * Абстракция IL-эмиттера.
 *
 * Контракт «IL корректен» (A-03): реализации гарантируют, что
 * `beginX`/`endX` парны, тело сбалансировано, заголовок есть.
 * `opcode`/`ldcI4`/`ldarg`/.../`label`/`declareLocal` разрешены
 * только внутри метода (`beginStaticMethod` ... `endStaticMethod`).
 *
 * Явные `begin/end` пары вместо лямбд `IlEmitter.() -> Unit`
 * (ADR 0005 — `this` в лямбде перекрывал бы `this@DotnetIrVisitor`).
 *
 * **A-04:** опкоды типизированы через [IlOpcode]. Общий метод
 * [opcode] покрывает опкод + строковые операнды; типизированные
 * хелперы ([ldcI4], [ldarg], [ldloc], [stloc], [ldstr], [br], ...)
 * инкапсулируют выбор коротких форм и экранирование — visitor
 * не знает про `ldc.i4.0` vs `ldc.i4 0`.
 *
 * TODO(BinaryIlEmitter): `IlOpcode → байт` + сериализация операндов.
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

    //=== Static method ===

    /** Открывает статический метод. `isEntrypoint` → `.entrypoint`. */
    fun beginStaticMethod(
        name: String,
        returnType: String,
        params: List<Pair<String, String>>,
        isEntrypoint: Boolean = false
    )

    /** Эмитит `.locals init (...)` если есть зарегистрированные локалки. */
    fun emitLocalsIfAny()

    /** Закрывает статический метод. */
    fun endStaticMethod()

    /** Сбрасывает состояние локалок/меток между методами. */
    fun resetMethodState()

    //=== Locals / labels ===

    /** Регистрирует локальную переменную, возвращает её индекс. */
    fun declareLocal(cilType: String): Int

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
     * Контракт: только внутри [beginStaticMethod] ... [endStaticMethod].
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

    //=== Result ===

    /** Возвращает собранный IL-текст (с проверкой, что всё закрыто). */
    fun text(): String
}
