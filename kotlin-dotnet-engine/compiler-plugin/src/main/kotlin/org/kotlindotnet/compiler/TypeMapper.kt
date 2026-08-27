package org.kotlindotnet.compiler

import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isAny
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNullableAny
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.isUByte
import org.jetbrains.kotlin.ir.types.isUInt
import org.jetbrains.kotlin.ir.types.isULong
import org.jetbrains.kotlin.ir.types.isUShort
import org.jetbrains.kotlin.ir.types.isUnit

/**
 * Maps Kotlin types to CIL types (see AGENTS.md §"Primitive Types").
 * PoC: covers only primitives and Unit/String/Any.
 */
object TypeMapper {

    fun mapType(type: IrType): String = when {
        type.isInt() -> "int32"
        type.isLong() -> "int64"
        type.isShort() -> "int16"
        type.isByte() -> "int8"
        type.isUInt() -> "uint32"
        type.isULong() -> "uint64"
        type.isUShort() -> "uint16"
        type.isUByte() -> "uint8"
        type.isDouble() -> "float64"
        type.isFloat() -> "float32"
        type.isBoolean() -> "bool"
        type.isChar() -> "char"
        type.isString() -> "string"
        type.isUnit() -> "void"
        type.isAny() || type.isNullableAny() -> "object"
        // TODO: Array<T>, List<T>, classes — Phase 8+
        type is IrSimpleType -> "object" // PoC fallback
        else -> "object"
    }
}
