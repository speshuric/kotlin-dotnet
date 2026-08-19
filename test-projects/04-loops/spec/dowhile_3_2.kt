// Адаптировано из kotlin spec-теста:
// .sources/kotlin/.../loop-statements/do-while-loop-statement/p-1/pos/3.2.kt
//
// KOTLIN CODEGEN BOX SPEC TEST (POSITIVE)
// SPEC VERSION: 0.1-253
// statements, loop-statements, do-while-loop-statement -> p1 -> s3
// do-while-loop-statement evaluates the loop condition after the loop body.

package org.example.loops.spec

fun box(): String {
    var x = 1
    do {
        x++
    } while (x < 2)
    if (x == 2)
        return "OK"
    return "NOK"
}

fun main() {
    println(box())
}
