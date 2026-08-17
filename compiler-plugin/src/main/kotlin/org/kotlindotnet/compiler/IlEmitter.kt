package org.kotlindotnet.compiler

/**
 * Билдер IL-текста (CIL assembly language).
 *
 * PoC: эмитит только то, что нужно для `test_add` (top-level static-методы
 * в sealed-классе `<File>Kt`). Классы, дженерики, поля — Phase 8+.
 *
 * Использует явные begin/end пары вместо лямбд, чтобы избежать
 * проблем с областью видимости (this в лямбде IlEmitter.() -> Unit
 * перекрывал бы this@DotnetIrVisitor).
 */
class IlEmitter {

    private val sb = StringBuilder()
    private var indent = ""

    fun text(): String = sb.toString()

    fun line(s: String = ""): IlEmitter {
        sb.append(indent).append(s).append('\n')
        return this
    }

    private fun pushIndent() { indent += "  " }
    private fun popIndent() { indent = indent.dropLast(2) }

    /** Эмитит заголовок сборки. */
    fun assemblyHeader(assemblyName: String) {
        line(".assembly extern mscorlib {}")
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
    }

    /** Открывает статический метод. */
    fun beginStaticMethod(
        name: String,
        returnType: String,
        params: List<Pair<String, String>>
    ) {
        val paramStr = params.joinToString(", ") { (t, n) -> "$t $n" }
        line(".method public hidebysig static $returnType $name($paramStr) cil managed")
        line("  {")
        pushIndent()
    }

    /** Закрывает статический метод. */
    fun endStaticMethod() {
        popIndent()
        line("  }")
    }
}
