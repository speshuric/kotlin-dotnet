package org.kotlindotnet.compiler

/**
 * Билдер IL-текста (CIL assembly language).
 *
 * PoC: эмитит top-level static-методы в sealed-классе `<File>Kt`.
 * Поддерживает locals, метки, арифметику, сравнения, ветвления.
 *
 * Использует явные begin/end пары вместо лямбд (см. ADR 0005 —
 * this в лямбде IlEmitter.() -> Unit перекрывал бы this@DotnetIrVisitor).
 */
class IlEmitter {

    private val sb = StringBuilder()
    private var indent = ""
    private var labelCounter = 0
    private var localsCount = 0
    private val locals = mutableListOf<String>()

    fun text(): String = sb.toString()

    fun line(s: String = ""): IlEmitter {
        sb.append(indent).append(s).append('\n')
        return this
    }

    private fun pushIndent() { indent += "  " }
    private fun popIndent() { indent = indent.dropLast(2) }

    /** Генерирует уникальную метку IL_<n>. */
    fun newLabel(): String = "IL_${labelCounter++}"

    /** Эмитит метку (на текущем отступе, без trailing). */
    fun label(name: String): IlEmitter {
        sb.append(name).append(":\n")
        return this
    }

    /** Регистрирует локальную переменную, возвращает её индекс. */
    fun declareLocal(cilType: String): Int {
        locals.add(cilType)
        return localsCount++
    }

    /** Эмитит заголовок сборки. */
    fun assemblyHeader(assemblyName: String, withRuntime: Boolean = false) {
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

    /** Открывает класс-контейнер для top-level функций. */
    fun beginContainerClass(namespace: String, className: String) {
        val full = if (namespace.isEmpty()) className else "$namespace.$className"
        line(".class public abstract sealed auto ansi $full extends [mscorlib]System.Object")
        line("{")
        pushIndent()
    }

    /** Закрывает класс-контейнер (+ эмитит default .ctor). */
    fun endContainerClass() {
        line(".method public hidebysig specialname rtspecialname")
        line("        instance void .ctor() cil managed")
        line("  {")
        line("    ldarg.0")
        line("    call instance void [mscorlib]System.Object::.ctor()")
        line("    ret")
        line("  }")
        popIndent()
        line("}")
    }

    /** Открывает статический метод. */
    fun beginStaticMethod(
        name: String,
        returnType: String,
        params: List<Pair<String, String>>,
        isEntrypoint: Boolean = false
    ) {
        val paramStr = params.joinToString(", ") { (t, n) -> "$t $n" }
        line(".method public hidebysig static $returnType $name($paramStr) cil managed")
        line("  {")
        pushIndent()
        if (isEntrypoint) {
            line(".entrypoint")
        }
    }

    /** Эмитит `.locals init` если есть локальные переменные. */
    fun emitLocalsIfAny() {
        if (locals.isNotEmpty()) {
            val items = locals.mapIndexed { i, t -> "$t V_$i" }.joinToString(", ")
            line(".locals init ($items)")
        }
    }

    /** Закрывает статический метод. */
    fun endStaticMethod() {
        popIndent()
        line("  }")
    }

    /** Сбрасывает состояние локалок/меток между методами. */
    fun resetMethodState() {
        locals.clear()
        localsCount = 0
        labelCounter = 0
    }
}
