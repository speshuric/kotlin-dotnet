@file:Suppress("unused")
@file:KotlinToPascalCase

// Test: annotation reading. Recognized annotations are compile-time
// instructions for the plugin; they do not change the emitted assembly,
// so the program itself just prints its number. Acceptance lives in the
// dump: the ir-dump must contain the recognized-annotations section with
// the file-level marker and both DotnetName values (see dump-grep).

@Target(AnnotationTarget.FILE)
annotation class KotlinToPascalCase

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
)
annotation class DotnetName(val name: String)

@DotnetName("RenamedClass")
class Marker {
    @DotnetName("renamedMember")
    fun check(): Int = 42
}

fun main() {
    println(Marker().check())
}
