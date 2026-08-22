// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Internal/MetadataWriterUtilities.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations: GetConstantTypeCode returns the raw ECMA-335 element-type
// byte instead of the reader-side SignatureTypeCode enum.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder

internal object MetadataWriterUtilities {
    // ELEMENT_TYPE_* codes relevant for constant values (ECMA-335 II.23.1.16)
    private const val ELEMENT_TYPE_BOOLEAN : Byte = 0x02
    private const val ELEMENT_TYPE_CHAR : Byte = 0x03
    private const val ELEMENT_TYPE_I1 : Byte = 0x04
    private const val ELEMENT_TYPE_U1 : Byte = 0x05
    private const val ELEMENT_TYPE_I2 : Byte = 0x06
    private const val ELEMENT_TYPE_U2 : Byte = 0x07
    private const val ELEMENT_TYPE_I4 : Byte = 0x08
    private const val ELEMENT_TYPE_U4 : Byte = 0x09
    private const val ELEMENT_TYPE_I8 : Byte = 0x0A
    private const val ELEMENT_TYPE_U8 : Byte = 0x0B
    private const val ELEMENT_TYPE_R4 : Byte = 0x0C
    private const val ELEMENT_TYPE_R8 : Byte = 0x0D
    private const val ELEMENT_TYPE_STRING : Byte = 0x0E
    private const val ELEMENT_TYPE_CLASS : Byte = 0x12

    /**
     * Returns the constant type code for a value passed to
     * [MetadataBuilder.addConstant] (see ECMA-335 II.22.9).
     */
    fun getConstantTypeCode(value: Any?): Byte {
        if (value == null) {
            // The encoding of Type for the nullref value for FieldInit is
            // ELEMENT_TYPE_CLASS with a Value of zero.
            return ELEMENT_TYPE_CLASS
        }

        return when (value) {
            is Int -> ELEMENT_TYPE_I4
            is String -> ELEMENT_TYPE_STRING
            is Boolean -> ELEMENT_TYPE_BOOLEAN
            is Char -> ELEMENT_TYPE_CHAR
            // Kotlin Byte is signed; the unsigned C# byte analog is UInt-based
            is Byte -> ELEMENT_TYPE_I1
            is Long -> ELEMENT_TYPE_I8
            is Double -> ELEMENT_TYPE_R8
            is Short -> ELEMENT_TYPE_I2
            is UShort -> ELEMENT_TYPE_U2
            is UInt -> ELEMENT_TYPE_U4
            is Float -> ELEMENT_TYPE_R4
            is ULong -> ELEMENT_TYPE_U8
            else -> throw IllegalArgumentException("invalid constant value of type ${value::class.qualifiedName}")
        }
    }

    fun serializeRowCounts(writer: BlobBuilder, rowCounts: IntArray) {
        for (rowCount in rowCounts) {
            if (rowCount > 0) {
                writer.writeInt32(rowCount)
            }
        }
    }
}
