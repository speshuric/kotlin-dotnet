package org.example.expr

public fun test_add(a: Int, b: Int) = a + b

public fun test_sub(a: Int, b: Int) = a - b

public fun test_mul(a: Int, b: Int) = a * b

public fun test_div(a: Int, b: Int) = a / b

public fun test_rem(a: Int, b: Int) = a % b

public fun test_neg(a: Int) = -a

public fun test_locals(a: Int, b: Int): Int {
    val sum = a + b
    val doubled = sum * 2
    return doubled
}

public fun test_if(a: Int): Int {
    if (a > 5) {
        return a * 2
    } else {
        return a - 1
    }
}

public fun test_when(a: Int): Int = when {
    a < 0 -> -a
    a == 0 -> 1
    else -> a
}

public fun test_compare(a: Int, b: Int): Boolean = a > b

public fun test_bool(a: Boolean, b: Boolean): Boolean = a && b || !a

public fun test_long(a: Long, b: Long) = a + b

public fun test_double(a: Double, b: Double) = a + b

public fun test_const() = 42

public fun test_string_eq(s: String) = s == "hello"
