// Classes: fields, constructors (including the synthesized default one),
// instance methods (callvirt), base-class inheritance, interface implementation,
// open/override - virtual dispatch through a base-typed variable.
//
// Expected output (checked by scripts/build-test.sh):
//   3
//   3
//   2
//   9
//   LP
//   shape
//   2
//   Rex
//   ok

package org.example.classes

interface Shape

class Circle(val r: Int) : Shape

open class Animal(val name: String) {
    open fun sound(): Int = 1
}

class Dog(name: String) : Animal(name) {
    override fun sound(): Int = 2
}

open class Point(val x: Int, val y: Int) {
    fun sum(): Int = x + y
}

class Counter {
    var count: Int = 0
    fun inc(): Int {
        count += 1
        return count
    }
}

class LabeledPoint(x: Int, y: Int, val label: String) : Point(x, y)

class Empty

fun main() {
    val p = Point(1, 2)
    println(p.sum()) // 3
    println(p.x + p.y) // 3

    val c = Counter()
    c.inc()
    c.inc()
    println(c.count) // 2

    val lp = LabeledPoint(4, 5, "LP")
    println(lp.sum()) // 9 - inherited method
    println(lp.label) // LP - property declared in the derived class

    val s: Shape = Circle(3)
    println("shape") // marker of interface implementation

    val a: Animal = Dog("Rex")
    println(a.sound()) // 2 — virtual dispatch
    println(a.name) // Rex

    val e = Empty()
    println("ok") // default ctor
}
