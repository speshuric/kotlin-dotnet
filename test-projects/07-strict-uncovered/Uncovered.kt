// Test (strict mode, negative): calls a kotlin.* member function with
// no coverage. The compile must fail listing the symbol; nothing here
// prints - the artifact never exists.

fun main() {
    val s = "abc"
    println(s.isEmpty())
}
