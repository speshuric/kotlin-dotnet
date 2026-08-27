package org.kotlindotnet.compiler

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import java.io.File

/**
 * Kotlin → .NET name mapping (see ADR 0005).
 *
 * PoC decisions:
 * - package → namespace: **verbatim** (lowercase, 1:1).
 * - top-level functions → the container `<FileName>Kt` (JVM mirror).
 * - method and class names: **verbatim** (including `_`, `<get-x>`).
 * - assembly name: from the file name (without extension).
 *
 * TODO: transform namespaces to PascalCase for .NET idiomacy.
 */
object NameMapper {

    /** Full name of the container class for the file's top-level functions. */
    fun containerClass(file: IrFile): String {
        val fileName = File(file.fileEntry.name).nameWithoutExtension
        return "${fileName}Kt"
    }

    /** User-defined class name — verbatim. */
    fun className(declaration: IrClass): String = declaration.name.asString()

    /** The namespace from the file's fqName (verbatim). */
    fun namespace(file: IrFile): String = file.packageFqName.asString()

    /** Assembly/module name from the file name (without extension). */
    fun assemblyName(file: IrFile): String {
        return File(file.fileEntry.name).nameWithoutExtension
    }

    /** Method name — verbatim. */
    fun methodName(function: IrSimpleFunction): String = function.name.asString()
}
