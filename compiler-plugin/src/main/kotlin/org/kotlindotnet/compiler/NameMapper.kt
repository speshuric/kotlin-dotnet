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

    /** Имя метода — verbatim. */
    fun methodName(function: IrSimpleFunction): String = function.name.asString()
}
