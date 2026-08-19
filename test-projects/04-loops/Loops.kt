package org.example.loops

fun test_while(): Int {
    var x = 0
    while (x < 10) { x++ }
    return x  // 10
}

fun test_do_while(): Int {
    var x = 0
    do { x++ } while (x < 10)
    return x  // 10
}

fun test_break(): Int {
    var i = 0
    while (true) { if (i == 5) break; i++ }
    return i  // 5
}

fun test_continue(): Int {
    // for (i in 0..10) — не реализован (Phase 11/G-02), переписано через while.
    var s = 0
    var i = 0
    while (i < 10) {
        i++
        if (i % 2 == 0) continue
        s += i
    }
    return s  // сумма нечётных 1+3+5+7+9 = 25
}

fun double(n: Int): Int = n * 2

fun test_call(): Int {
    return double(21)  // 42
}

fun test_concat(): String {
    val x = 42
    return "x = $x"  // "x = 42"
}

fun main() {
    println(test_while())      // 10
    println(test_do_while())   // 10
    println(test_break())      // 5
    println(test_continue())   // 25
    println(test_call())       // 42
    println(test_concat())     // x = 42
}
