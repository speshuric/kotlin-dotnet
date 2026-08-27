package org.kotlindotnet.compiler.util

/**
 * A thin logging facade for the compiler plugin.
 *
 * - Every message automatically gets the `[kotlin-dotnet]` prefix.
 * - The level is set by the system property `kotlin.dotnet.log.level`
 *   (INFO/WARN/ERROR; default INFO).
 * - Output goes directly to `System.err` (the compiler captures stderr).
 * - No slf4j/logback: the facade is deliberately thin (≤ 50 lines).
 *
 * The only place in `compiler-plugin/` where the string `"kotlin-dotnet"` lives.
 */
object Log {

    private const val PREFIX = "[kotlin-dotnet] "

    private enum class Level { INFO, WARN, ERROR }

    private val level: Level = run {
        val raw = System.getProperty("kotlin.dotnet.log.level", "INFO")?.uppercase()
        when (raw) {
            "WARN" -> Level.WARN
            "ERROR" -> Level.ERROR
            else -> Level.INFO
        }
    }

    fun info(msg: String) {
        if (level.ordinal <= Level.INFO.ordinal) System.err.println(PREFIX + msg)
    }

    fun warn(msg: String) {
        if (level.ordinal <= Level.WARN.ordinal) System.err.println(PREFIX + msg)
    }

    fun error(msg: String, throwable: Throwable? = null) {
        System.err.println(PREFIX + msg)
        throwable?.printStackTrace(System.err)
    }
}
