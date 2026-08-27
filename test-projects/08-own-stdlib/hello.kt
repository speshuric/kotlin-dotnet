// Test: the same hello as 03-hello, compiled on our own
// kotlin-dotnet-stdlib instead of the stock one (own-stdlib=true):
// println comes from libraries/stdlib shims via -no-stdlib + our jar.

fun main() {
    println("Hello, .NET!")
}
