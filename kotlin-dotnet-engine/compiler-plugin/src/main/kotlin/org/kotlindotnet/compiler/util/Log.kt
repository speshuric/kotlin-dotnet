package org.kotlindotnet.compiler.util

/**
 * Тонкий фасад логирования компилятор-плагина.
 *
 * - Все сообщения автоматически получают префикс `[kotlin-dotnet]`.
 * - Уровень задаётся системным свойством `kotlin.dotnet.log.level`
 *   (INFO/WARN/ERROR; default INFO).
 * - Вывод — напрямую в `System.err` (компилятор перехватывает stderr).
 * - Без slf4j/logback: фасад намеренно тонкий (≤ 50 строк).
 *
 * Единственное место в `compiler-plugin/`, где живёт строка `"kotlin-dotnet"`.
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
