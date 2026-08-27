package org.kotlindotnet.compiler.pe

import org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataTokens
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle

/**
 * The pe backend's data model: metadata table rows in insertion order
 * (ADR 0010). The group order is fixed — &lt;Module&gt;, the container of
 * top-level functions, user-defined classes — and handle precomputation rests
 * on it ([PeIlEmitter]).
 */
internal class FieldRec(val cilType: String, val name: String) {
    var handle: FieldDefinitionHandle = MetadataTokens.fieldDefinitionHandle(0)
}

/** Local variable: CIL type plus the IR name used in the PDB. */
internal data class LocalRec(val cilType: String, val name: String?)

internal class MethodRec(
    val name: String,
    val retCil: String,
    val paramsCil: List<String>,
    val isEntrypoint: Boolean,
    /** An instance method or ctor (HASTHIS in the signature, arg0 = this). */
    val isInstance: Boolean,
    val isCtor: Boolean,
    /** Explicit MethodAttributes (open/override); null → per [attributes]. */
    val attributesOverride: UInt? = null,
) {
    /** Default MethodAttributes: ctor `0x1886`, instance `0x0086`, static `0x0096`. */
    val attributes: Int =
        when {
            isCtor -> 0x1886 // Public | HideBySig | SpecialName | RTSpecialName
            isInstance -> 0x0086 // Public | HideBySig
            else -> 0x0096 // Public | Static | HideBySig
        }

    val effectiveAttributes: Int =
        attributesOverride?.toInt() ?: attributes

    val ops = mutableListOf<Op>()
    val locals = mutableListOf<LocalRec>()

    /** Sequence points marked by the visitor; offsets are filled by the encoder. */
    val seqPoints = mutableListOf<SequencePoint>()

    var handle: MethodDefinitionHandle = MetadataTokens.methodDefinitionHandle(0)
    var bodyOffset = -1
}

internal class ClassRec(
    val namespace: String,
    val name: String,
    val flags: UInt,
    val baseTypeCil: String?,
    val interfaces: List<String>,
) {
    val fields = mutableListOf<FieldRec>()
    val methods = mutableListOf<MethodRec>()
}

/** TypeAttributes.Interface (used for extends=nil on interfaces). */
internal const val INTERFACE_FLAG: UInt = 0x0020u

internal const val SYSTEM_RUNTIME_ASSEMBLY = "System.Runtime"
