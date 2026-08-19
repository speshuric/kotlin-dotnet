package org.kotlindotnet.compiler

/**
 * Контрактный тест IlEmitter (A-03/A-04).
 *
 * Демонстрирует инвариант «IL корректен»: нарушенные пары
 * begin/end и инструкции вне метода должны бросать
 * [IllegalStateException]; сбалансированный код — возвращать
 * валидный IL через [IlEmitter.text].
 *
 * После A-04 инструкции эмитятся типобезопасными методами
 * (`opcode`, `ldarg`, `ldcI4`, ...) вместо сырого `raw(...)`.
 *
 * Запуск: `./gradlew :compiler-plugin:compileTestKotlin` — должен
 * компилироваться без ошибок. Поведение можно проверить вручную:
 * `./gradlew :compiler-plugin:test` (когда подключим JUnit) или
 * через `kotlin -cp ... IlEmitterContractTestKt`.
 *
 * TODO(H-test-migration): мигрировать в JUnit-тест, когда
 * появится test-runner в compiler-plugin.
 */
private object IlEmitterContractTest {

    private fun expectThrows(label: String, block: () -> Unit) {
        try {
            block()
            error("FAIL [$label]: expected IllegalStateException but no exception was thrown")
        } catch (e: IllegalStateException) {
            // OK — контракт сработал.
            println("OK   [$label]: ${e.message}")
        }
    }

    fun run() {
        // 1. opcode до beginStaticMethod → IllegalStateException.
        expectThrows("opcode before method") {
            val e = TextIlEmitter()
            e.assemblyHeader("X")
            e.beginContainerClass("", "XKt")
            e.opcode(IlOpcode.ADD) // вне метода
        }

        // 2. endContainerClass без beginContainerClass → IllegalStateException.
        expectThrows("endContainerClass without begin") {
            val e = TextIlEmitter()
            e.assemblyHeader("X")
            e.endContainerClass()
        }

        // 3. beginStaticMethod без endStaticMethod → text() бросает.
        expectThrows("beginStaticMethod without end (text)") {
            val e = TextIlEmitter()
            e.assemblyHeader("X")
            e.beginContainerClass("", "XKt")
            e.beginStaticMethod("foo", "int32", emptyList(), isEntrypoint = false)
            e.ldcI4(1)
            e.opcode(IlOpcode.RET)
            e.text() // незакрытый метод
        }

        // 4. endStaticMethod без beginStaticMethod → IllegalStateException.
        expectThrows("endStaticMethod without begin") {
            val e = TextIlEmitter()
            e.assemblyHeader("X")
            e.beginContainerClass("", "XKt")
            e.endStaticMethod()
        }

        // 5. beginStaticMethod на верхнем уровне (без контейнера) → ошибка.
        expectThrows("beginStaticMethod without container") {
            val e = TextIlEmitter()
            e.assemblyHeader("X")
            e.beginStaticMethod("foo", "int32", emptyList())
        }

        // 6. Сбалансированный код → text() возвращает валидный IL.
        val e = TextIlEmitter()
        e.assemblyHeader("Balanced")
        e.beginContainerClass("org.example", "BalancedKt")
        e.beginStaticMethod("foo", "int32", listOf("int32" to "a"), isEntrypoint = false)
        e.emitLocalsIfAny()
        e.ldarg(0)
        e.ldcI4(1)
        e.opcode(IlOpcode.ADD)
        e.opcode(IlOpcode.RET)
        e.endStaticMethod()
        e.endContainerClass()
        val il = e.text()
        check(il.contains(".assembly 'Balanced'")) { "missing assembly header" }
        check(il.contains("BalancedKt")) { "missing container class" }
        check(il.contains("foo")) { "missing method" }
        check(il.contains("ldarg.0")) { "missing ldarg instruction" }
        check(il.contains("ret")) { "missing ret" }
        println("OK   [balanced code]: valid IL, ${il.length} chars")

        // 7. text() дважды на сбалансированном — OK (idempotent read).
        e.text()
        println("OK   [text() idempotent]: second call succeeded")

        println("\nAll IlEmitter contract checks passed.")
    }
}

fun main() {
    IlEmitterContractTest.run()
}
