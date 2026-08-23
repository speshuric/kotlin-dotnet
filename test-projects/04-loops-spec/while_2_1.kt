// Адаптировано из kotlin spec-теста:
// .sources/kotlin/.../loop-statements/while-loop-statement/p-1/pos/2.1.kt
//
// KOTLIN CODEGEN BOX SPEC TEST (POSITIVE)
// SPEC VERSION: 0.1-253
// statements, loop-statements, while-loop-statement -> p1 -> s2
// while-loop-statement evaluates the loop condition before the loop body.

package org.example.loops.spec

fun box(): String {
    var x = 1
    var cond = true
    while (cond) {
        x++
        if (x == 5) cond = false
    }
    if (x == 5)
        return "OK"
    return "NOK"
}

fun main() {
    println(box())
}
