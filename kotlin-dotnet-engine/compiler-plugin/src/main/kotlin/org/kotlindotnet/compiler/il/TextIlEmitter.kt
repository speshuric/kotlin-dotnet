package org.kotlindotnet.compiler.il

/**
 * Текстовый IL-эмиттер (CIL assembly language) — реализация [IlEmitter].
 *
 * PoC: эмитит top-level static-методы в sealed-классе `<File>Kt`.
 * Поддерживает locals, метки, арифметику, сравнения, ветвления.
 *
 * **Контракт «IL корректен» (A-03):** ведёт стек [IlContext]
 * (`TopLevel` → `ContainerClass` → `Method`). Каждый `beginX`
 * пушит, `endX` — попает с проверкой парности. `opcode`/`label`/
 * `declareLocal` вне метода → [IllegalStateException]. В [text]
 * проверяется, что стек пустой (всё закрыто).
 *
 * Использует явные begin/end пары вместо лямбд (ADR 0005 — `this`
 * в лямбде `IlEmitter.() -> Unit` перекрывал бы `this@DotnetIrVisitor`).
 *
 * **A-04:** инкапсулирует короткие формы CIL (`ldc.i4.0`...`ldc.i4.8`,
 * `ldarg.0`...`ldarg.3`, `ldloc.0`...`ldloc.3`, `stloc.0`...`stloc.3`)
 * — visitor не знает про них. `ldc.i4.M1` — для -1. Экранирование
 * строк (`ldstr`) также инкапсулировано здесь.
 *
 * TODO(BinaryIlEmitter): `IlOpcode → байт` + сериализация операндов.
 */
@Deprecated(
    message = "IL-текст + ilasm заменены PE-бэкендом (ADR 0010, S6). " +
        "Используется только fallback-путём backend=il; будет удалён.",
    replaceWith = ReplaceWith("org.kotlindotnet.compiler.pe.PeIlEmitter"),
)
class TextIlEmitter : IlEmitter {

    private val sb = StringBuilder()
    private var indent = ""
    private var labelCounter = 0
    private var localsCount = 0
    private val locals = mutableListOf<String>()

    /**
     * Стек контекстов. Индекс 0 = `TopLevel` (всегда присутствует,
     * пока assembly не закрыт).
     */
    private val contextStack = mutableListOf<IlContext>(IlContext.TopLevel)

    private fun currentContext(): IlContext =
        contextStack.lastOrNull() ?: error("IlEmitter context stack is empty")

    private fun requireTopLevel(op: String) {
        if (currentContext() != IlContext.TopLevel) {
            throw IllegalStateException(
                "Contract violation ($op): expected TopLevel, but was ${currentContext()}"
            )
        }
    }

    private fun requireContainerClass(op: String) {
        if (currentContext() !is IlContext.ContainerClass) {
            throw IllegalStateException(
                "Contract violation ($op): expected ContainerClass, but was ${currentContext()}"
            )
        }
    }

    private fun requireInMethod(op: String) {
        if (currentContext() !is IlContext.Method) {
            throw IllegalStateException(
                "Contract violation ($op): allowed only inside beginStaticMethod..endStaticMethod, " +
                    "but context was ${currentContext()}"
            )
        }
    }

    /** Эмитит строку с текущим отступом. Деталь реализации — приватный. */
    private fun line(s: String = "") {
        sb.append(indent).append(s).append('\n')
    }

    private fun pushIndent() { indent += "  " }
    private fun popIndent() { indent = indent.dropLast(2) }

    // === IlEmitter: assembly / module ===

    override fun assemblyHeader(assemblyName: String, withRuntime: Boolean) {
        line(".assembly extern mscorlib {}")
        if (withRuntime) {
            line(".assembly extern KotlinDotnetRuntime {}")
        }
        line(".assembly '$assemblyName'")
        line("{")
        line("  .ver 0:0:0:0")
        line("}")
        line(".module '$assemblyName.dll'")
        line()
    }

    // === IlEmitter: container class ===

    override fun beginContainerClass(namespace: String, className: String) {
        requireTopLevel("beginContainerClass")
        val full = if (namespace.isEmpty()) className else "$namespace.$className"
        line(".class public abstract sealed auto ansi $full extends [mscorlib]System.Object")
        line("{")
        pushIndent()
        contextStack.add(IlContext.ContainerClass(namespace, className))
    }

    override fun endContainerClass() {
        val cur = currentContext()
        if (cur !is IlContext.ContainerClass) {
            throw IllegalStateException(
                "Contract violation (endContainerClass): no matching beginContainerClass " +
                    "(context was $cur)"
            )
        }
        // default .ctor
        line(".method public hidebysig specialname rtspecialname")
        line("        instance void .ctor() cil managed")
        line("  {")
        line("    ldarg.0")
        line("    call instance void [mscorlib]System.Object::.ctor()")
        line("    ret")
        line("  }")
        popIndent()
        line("}")
        contextStack.removeAt(contextStack.lastIndex)
    }

    // === IlEmitter: user classes (Phase 10) — il-путь заморожен на Phase 9 (D1) ===

    override fun beginClass(
        namespace: String,
        name: String,
        flags: UInt,
        baseTypeCil: String?,
        interfaces: List<String>
    ) {
        TODO("removed after Phase 10: user classes are pe-backend only (D1)")
    }

    override fun endClass() {
        TODO("removed after Phase 10: user classes are pe-backend only (D1)")
    }

    override fun declareField(cilType: String, name: String, isStatic: Boolean): Int {
        TODO("removed after Phase 10: user classes are pe-backend only (D1)")
    }

    // === IlEmitter: static method ===

    override fun beginMethod(
        name: String,
        returnType: String,
        params: List<Pair<String, String>>,
        isStatic: Boolean,
        isEntrypoint: Boolean,
        attributesOverride: UInt?
    ) {
        if (!isStatic) {
            TODO("removed after Phase 10: instance methods are pe-backend only (D1)")
        }
        beginStaticMethod(name, returnType, params, isEntrypoint)
    }

    override fun beginConstructor(params: List<Pair<String, String>>) {
        TODO("removed after Phase 10: user classes are pe-backend only (D1)")
    }

    override fun beginStaticMethod(
        name: String,
        returnType: String,
        params: List<Pair<String, String>>,
        isEntrypoint: Boolean
    ) {
        requireContainerClass("beginStaticMethod")
        val paramStr = params.joinToString(", ") { (t, n) -> "$t $n" }
        line(".method public hidebysig static $returnType $name($paramStr) cil managed")
        line("  {")
        pushIndent()
        if (isEntrypoint) {
            line(".entrypoint")
        }
        contextStack.add(IlContext.Method(name, returnType, isEntrypoint))
    }

    override fun emitLocalsIfAny() {
        requireInMethod("emitLocalsIfAny")
        if (locals.isNotEmpty()) {
            val items = locals.mapIndexed { i, t -> "$t V_$i" }.joinToString(", ")
            line(".locals init ($items)")
        }
    }

    override fun endStaticMethod() {
        requireInMethod("endStaticMethod")
        popIndent()
        line("  }")
        contextStack.removeAt(contextStack.lastIndex)
    }

    override fun resetMethodState() {
        // Допускается вызывать перед beginStaticMethod (подготовка состояния).
        locals.clear()
        localsCount = 0
        labelCounter = 0
    }

    // === IlEmitter: locals / labels ===

    override fun declareLocal(cilType: String): Int {
        // Разрешаем объявлять локалку перед emitLocalsIfAny, но строго
        // внутри метода (visitor вызывает resetMethodState + declareLocal
        // между beginStaticMethod и emitLocalsIfAny).
        requireInMethod("declareLocal")
        locals.add(cilType)
        return localsCount++
    }

    override fun newLabel(): String {
        requireInMethod("newLabel")
        return "IL_${labelCounter++}"
    }

    override fun label(name: String) {
        requireInMethod("label")
        sb.append(name).append(":\n")
    }

    // === IlEmitter: instructions (A-04) ===

    override fun opcode(op: IlOpcode, vararg operands: String) {
        requireInMethod("opcode")
        if (operands.isEmpty()) {
            line(op.il)
        } else {
            line("${op.il} ${operands.joinToString(" ")}")
        }
    }

    // --- Загрузка констант ---

    override fun ldcI4(value: Int) {
        requireInMethod("ldcI4")
        line(ldcI4Text(value))
    }

    override fun ldcI8(value: Long) {
        requireInMethod("ldcI8")
        // Малые значения переиспользуют ldc.i4-формы (согласовано с
        // предыдущим поведением visitor: Long 0..8 и -1 → ldc.i4.*).
        if (value in 0L..8L) {
            line(ldcI4Text(value.toInt()))
        } else if (value == -1L) {
            line("ldc.i4.M1")
        } else {
            line("ldc.i8 $value")
        }
    }

    override fun ldcR4(value: Float) {
        requireInMethod("ldcR4")
        line("ldc.r4 $value")
    }

    override fun ldcR8(value: Double) {
        requireInMethod("ldcR8")
        line("ldc.r8 $value")
    }

    // --- Загрузка/сохранение ---

    override fun ldarg(index: Int) {
        requireInMethod("ldarg")
        line(ldargText(index))
    }

    override fun ldloc(index: Int) {
        requireInMethod("ldloc")
        line(ldlocText(index))
    }

    override fun stloc(index: Int) {
        requireInMethod("stloc")
        line(stlocText(index))
    }

    override fun ldstr(s: String) {
        requireInMethod("ldstr")
        line("ldstr \"${escapeIlString(s)}\"")
    }

    // --- Управление потоком ---

    override fun br(label: String) = opcode(IlOpcode.BR, label)
    override fun brtrue(label: String) = opcode(IlOpcode.BRTRUE, label)
    override fun brfalse(label: String) = opcode(IlOpcode.BRFALSE, label)

    // === IlEmitter: box / discard / массивы ===

    override fun box(cilType: String) = opcode(IlOpcode.BOX, cilType)
    override fun pop() = opcode(IlOpcode.POP)
    override fun dup() = opcode(IlOpcode.DUP)
    override fun newarr(cilType: String) = opcode(IlOpcode.NEWARR, cilType)
    override fun stelemRef() = opcode(IlOpcode.STELEM_REF)

    // === IlEmitter: result ===

    override fun text(): String {
        if (contextStack.size != 1 || contextStack.last() != IlContext.TopLevel) {
            throw IllegalStateException(
                "Contract violation (text): context stack is not empty " +
                    "(unclosed: ${contextStack.drop(1)}). " +
                    "All beginX must be closed by endX before text()."
            )
        }
        return sb.toString()
    }

    // === Приватные хелперы: короткие формы CIL ===

    /**
     * Текст ldc.i4-инструкции для целого значения.
     * Короткие формы: `ldc.i4.M1` для -1, `ldc.i4.0`...`ldc.i4.8` для 0..8.
     */
    private fun ldcI4Text(value: Int): String = when (value) {
        -1 -> "ldc.i4.M1"
        in 0..8 -> "ldc.i4.$value"
        else -> "ldc.i4 $value"
    }

    /** Текст ldarg-инструкции: короткие формы `ldarg.0`...`ldarg.3`. */
    private fun ldargText(index: Int): String = when (index) {
        in 0..3 -> "ldarg.$index"
        else -> "ldarg $index"
    }

    /** Текст ldloc-инструкции: короткие формы `ldloc.0`...`ldloc.3`. */
    private fun ldlocText(index: Int): String = when (index) {
        in 0..3 -> "ldloc.$index"
        else -> "ldloc $index"
    }

    /** Текст stloc-инструкции: короткие формы `stloc.0`...`stloc.3`. */
    private fun stlocText(index: Int): String = when (index) {
        in 0..3 -> "stloc.$index"
        else -> "stloc $index"
    }

    /**
     * Экранирование строкового литерала для CIL `ldstr`.
     * Инкапсулировано в эмиттере (A-04) — visitor не знает про экранирование.
     */
    private fun escapeIlString(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
