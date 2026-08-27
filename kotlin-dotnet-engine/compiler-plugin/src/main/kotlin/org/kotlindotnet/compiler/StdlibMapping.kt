package org.kotlindotnet.compiler

/**
 * Declarative registry of stdlib-to-runtime redirects (stage A of the
 * stdlib strategy, see docs/stdlib-design.md and ADR 0014).
 *
 * One [Entry] per symbol: the record fields mirror what a redirect
 * annotation on a shim declaration will carry (fully qualified Kotlin
 * name + target owner/method in the runtime assembly), so converting the
 * registry into annotated shims is mechanical.
 *
 * Parameter signatures are NOT stored here: overload selection happens at
 * the call site from the actual argument types, matching how Print.cs
 * declares overloads (println(object?/string/int/...)). Empty-argument
 * calls resolve to the parameterless overload.
 *
 * Later (stage B) this table feeds only symbols whose shims are not yet
 * annotated; resolution will prefer IR annotations first.
 */
object StdlibMapping {

    /** Runtime assembly referenced by emitted CALL targets. */
    const val RUNTIME_ASSEMBLY: String = "KotlinDotnetRuntime"

    /** Namespace holding runtime entry-point classes (Kotlin.Runtime.*). */
    const val RUNTIME_NAMESPACE: String = "Kotlin.Runtime"

    /**
     * Redirect target for one stdlib symbol.
     *
     * @property fqName      fully qualified Kotlin name (kotlin.io.println)
     * @property owner       CIL type reference inside the runtime namespace
     *                       (e.g. Print for Kotlin.Runtime.Print)
     * @property method      .NET method name of the target entry point;
     *                       may differ from the Kotlin name once renames
     *                       appear (@DotnetName / name-mapping track)
     */
    data class Entry(
        val fqName: String,
        val owner: String,
        val method: String,
    )

    // Order is irrelevant: lookup goes through the map. Keep entries
    // sorted by fqName for readable diffs when new symbols land.
    private val ENTRIES: List<Entry> = listOf(
        Entry(fqName = "kotlin.io.print", owner = "Print", method = "print"),
        Entry(fqName = "kotlin.io.println", owner = "Print", method = "println"),
    )

    private val BY_FQ_NAME: Map<String, Entry> =
        ENTRIES.associateBy { it.fqName }

    /** Registry entry for the given fully qualified Kotlin name, or null. */
    fun lookup(fqName: String): Entry? = BY_FQ_NAME[fqName]

    /**
     * Full CALL operand for the entry: void [asm]Ns.Owner::method(params)
     * (params joined with ", "; empty for the parameterless overload).
     */
    fun callSignature(entry: Entry, paramCilTypes: List<String>): String {
        val params = if (paramCilTypes.isEmpty()) "" else paramCilTypes.joinToString(", ")
        val nsOwner = if (entry.owner.contains('.')) entry.owner else "${RUNTIME_NAMESPACE}.${entry.owner}"
        return "void [$RUNTIME_ASSEMBLY]$nsOwner::${entry.method}($params)"
    }
}
