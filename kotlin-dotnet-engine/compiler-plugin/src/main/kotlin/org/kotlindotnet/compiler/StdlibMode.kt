package org.kotlindotnet.compiler

/**
 * Strictness of stdlib coverage accounting (stdlib strategy Р3, ADR 0014).
 *
 * LENIENT (transitional default): uncovered kotlin.* calls keep today's
 * behavior; each one also produces a warning via Log.
 *
 * STRICT: the module pass collects every uncovered call and the plugin
 * fails after emission with a single summary - no silent gaps in the
 * produced binary. K2 diagnostics cannot be raised from an IR extension,
 * so failing through an exception is the PoC mechanism here.
 */
enum class StdlibMode {
    LENIENT,
    STRICT;

    companion object {
        /** CLI value -> mode ("lenient"/"strict"; case-insensitive). */
        fun fromCli(value: String): StdlibMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: error("Unknown stdlib.mode: $value (expected lenient|strict)")
    }
}

/**
 * Collects stdlib symbols referenced by a compilation unit that have no
 * redirect / emit path yet. One collector per IrFile visitor run; the
 * extension decides what happens to the tally depending on
 * [StdlibMode].
 *
 * Accounting covers calls to kotlin.* declarations that reach the
 * visitor's stdlib resolution point without a registry match - i.e. the
 * cases that would otherwise compile into either a JVM-target body
 * reference (unresolvable at runtime) or a silent fallback. Calls the
 * emitter handles natively (operators on primitives, string length via
 * instance dispatch on System.String) never enter this funnel.
 */
class UncoveredStdlibCollector {

    /** fqName of the callee -> number of references seen. */
    private val counts = LinkedHashMap<String, Int>()

    val isEmpty: Boolean get() = counts.isEmpty()

    fun record(fqName: String) {
        counts.merge(fqName, 1, Int::plus)
    }

    /**
     * Sorted "name xN" lines; deterministic output for stable tests.
     * Functions come back once per distinct symbol regardless of how many
     * times it was called.
     */
    fun summaryLines(): List<String> =
        counts.entries.sortedBy { it.key }.map { (fqName, n) ->
            if (n == 1) fqName else "$fqName x$n"
        }

    /**
     * Warnings for lenient mode; one line per distinct symbol.
     */
    fun reportLenient(sourceName: String) {
        for (line in summaryLines()) {
            org.kotlindotnet.compiler.util.Log.warn(
                "uncovered stdlib symbol in $sourceName: $line",
            )
        }
    }

    /**
     * Single failure listing every uncovered symbol; strict mode only.
     */
    fun failStrict(sourceName: String): Nothing {
        val list = summaryLines().joinToString("\n  ")
        throw IllegalStateException(
            "Uncovered kotlin.* symbols in '$sourceName' (stdlib.mode=strict):\n  $list\n" +
                "Either remove these usages, or switch to stdlib.mode=lenient.",
        )
    }
}
