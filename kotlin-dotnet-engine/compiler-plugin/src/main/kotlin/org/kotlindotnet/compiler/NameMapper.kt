package org.kotlindotnet.compiler

import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import java.io.File

/**
 * Мэппинг имён Kotlin → .NET (см. ADR 0005).
 *
 * PoC-решения:
 * - package → namespace: **verbatim** (lowercase, 1:1).
 * - top-level functions → контейнер `<FileName>Kt` (JVM-mirror).
 * - имена методов: **verbatim** (включая `_`).
 * - assembly name: из имени файла (без расширения).
 *
 * TODO: namespace-трансформация в PascalCase для идиоматичности .NET.
 */
object NameMapper {

    /** Полное имя класса-контейнера для top-level функций файла. */
    fun containerClass(file: IrFile): String {
        val fileName = File(file.fileEntry.name).nameWithoutExtension
        return "${fileName}Kt"
    }

    /** Namespace из fqName файла (verbatim). */
    fun namespace(file: IrFile): String = file.packageFqName.asString()

    /** Имя сборки/модуля из имени файла (без расширения). */
    fun assemblyName(file: IrFile): String {
        return File(file.fileEntry.name).nameWithoutExtension
    }

    /**
     * Имя метода — verbatim. Если имя совпадает с CIL-опкодом (напр. `box`,
     * `add`, `pop`), ilasm падает на синтаксической ошибке, поэтому такое
     * имя заключается в одинарные кавычки: `'box'`. См. Phase 9 spec-тесты
     * (`fun box(): String`).
     */
    fun methodName(function: IrSimpleFunction): String {
        val name = function.name.asString()
        return if (isCilReservedWord(name)) "'$name'" else name
    }

    /**
     * Проверка, является ли [word] CIL-опкодом/ключевым словом, которое
     * конфликтует с именем метода. Минимальный набор (расширять по мере
     * необходимости).
     */
    private fun isCilReservedWord(word: String): Boolean = word in RESERVED_CIL_WORDS

    private val RESERVED_CIL_WORDS = setOf(
        // CIL opcodes, которые могут оказаться именем метода/поля.
        "box", "unbox", "pop", "dup", "add", "sub", "mul", "div", "rem",
        "and", "or", "xor", "shl", "shr", "neg", "not", "ret", "call",
        "br", "brtrue", "brfalse", "ceq", "cgt", "clt", "ldnull", "ldstr",
        "newobj", "newarr", "ldarga", "ldloca", "stind", "ldind",
    )
}
