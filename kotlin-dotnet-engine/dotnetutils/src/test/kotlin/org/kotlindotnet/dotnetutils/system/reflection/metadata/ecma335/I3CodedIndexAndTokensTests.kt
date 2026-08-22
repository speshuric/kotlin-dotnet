// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Adapted from System.Reflection.Metadata tests (Ecma335/CodedIndexTests.cs,
// Ecma335/MetadataTokensTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Scope note: TagToTokenTests is not portable — it reflects over the
// reader-side tag tables that are not part of the port; its coverage of
// encode results is provided here via the CodedIndex vectors.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GuidHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.Handle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.HandleKind
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.UserStringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.getBranchOperandSize
import org.kotlindotnet.dotnetutils.system.reflection.metadata.getLongBranch
import org.kotlindotnet.dotnetutils.system.reflection.metadata.getShortBranch
import org.kotlindotnet.dotnetutils.system.reflection.metadata.isBranch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class I3CodedIndexAndTokensTests {

    // --- Errors (all families reject a CustomAttribute row handle) ---

    @Test
    fun errors() {
        // a CustomAttribute row handle is not valid for any coded index above
        val bad = MetadataTokens.customAttributeHandle(1).toEntityHandle()
        assertFailsWith<IllegalArgumentException> { CodedIndex.hasCustomAttribute(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.hasConstant(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.customAttributeType(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.hasDeclSecurity(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.hasFieldMarshal(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.hasSemantics(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.implementation(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.memberForwarded(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.memberRefParent(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.methodDefOrRef(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.resolutionScope(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.typeDefOrRef(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.typeDefOrRefOrSpec(bad) }
        assertFailsWith<IllegalArgumentException> { CodedIndex.typeOrMethodDef(bad) }
    }

    @Test
    fun hasCustomAttribute() {
        assertEquals(0, CodedIndex.hasCustomAttribute(MetadataTokens.methodDefinitionHandle(0).toEntityHandle()))
        assertEquals(
            (0xffffff shl 5) or 21,
            CodedIndex.hasCustomAttribute(MetadataTokens.methodSpecificationHandle(0xffffff).toEntityHandle()),
        )

        assertEquals(0x20 or 0, CodedIndex.hasCustomAttribute(MetadataTokens.methodDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x20 or 1, CodedIndex.hasCustomAttribute(MetadataTokens.fieldDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x20 or 2, CodedIndex.hasCustomAttribute(MetadataTokens.typeReferenceHandle(1).toEntityHandle()))
        assertEquals(0x20 or 3, CodedIndex.hasCustomAttribute(MetadataTokens.typeDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x20 or 4, CodedIndex.hasCustomAttribute(MetadataTokens.parameterHandle(1).toEntityHandle()))
        assertEquals(0x20 or 5, CodedIndex.hasCustomAttribute(MetadataTokens.interfaceImplementationHandle(1).toEntityHandle()))
        assertEquals(0x20 or 6, CodedIndex.hasCustomAttribute(MetadataTokens.memberReferenceHandle(1).toEntityHandle()))
        assertEquals(0x20 or 7, CodedIndex.hasCustomAttribute(EntityHandle.MODULE_DEFINITION))
        assertEquals(0x20 or 8, CodedIndex.hasCustomAttribute(MetadataTokens.declarativeSecurityAttributeHandle(1).toEntityHandle()))
        assertEquals(0x20 or 9, CodedIndex.hasCustomAttribute(MetadataTokens.propertyDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x20 or 10, CodedIndex.hasCustomAttribute(MetadataTokens.eventDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x20 or 11, CodedIndex.hasCustomAttribute(MetadataTokens.standaloneSignatureHandle(1).toEntityHandle()))
        assertEquals(0x20 or 12, CodedIndex.hasCustomAttribute(MetadataTokens.moduleReferenceHandle(1).toEntityHandle()))
        assertEquals(0x20 or 13, CodedIndex.hasCustomAttribute(MetadataTokens.typeSpecificationHandle(1).toEntityHandle()))
        assertEquals(0x20 or 14, CodedIndex.hasCustomAttribute(EntityHandle.ASSEMBLY_DEFINITION))
        assertEquals(0x20 or 15, CodedIndex.hasCustomAttribute(MetadataTokens.assemblyReferenceHandle(1).toEntityHandle()))
        assertEquals(0x20 or 16, CodedIndex.hasCustomAttribute(MetadataTokens.assemblyFileHandle(1).toEntityHandle()))
        assertEquals(0x20 or 17, CodedIndex.hasCustomAttribute(MetadataTokens.exportedTypeHandle(1).toEntityHandle()))
        assertEquals(0x20 or 18, CodedIndex.hasCustomAttribute(MetadataTokens.manifestResourceHandle(1).toEntityHandle()))
        assertEquals(0x20 or 19, CodedIndex.hasCustomAttribute(MetadataTokens.genericParameterHandle(1).toEntityHandle()))
        assertEquals(0x20 or 20, CodedIndex.hasCustomAttribute(MetadataTokens.genericParameterConstraintHandle(1).toEntityHandle()))
        assertEquals(0x20 or 21, CodedIndex.hasCustomAttribute(MetadataTokens.methodSpecificationHandle(1).toEntityHandle()))
    }

    @Test
    fun hasConstant() {
        assertEquals(0, CodedIndex.hasConstant(MetadataTokens.fieldDefinitionHandle(0).toEntityHandle()))
        assertEquals(
            (0xffffff shl 2) or 2,
            CodedIndex.hasConstant(MetadataTokens.propertyDefinitionHandle(0xffffff).toEntityHandle()),
        )
        assertEquals(0x04 or 0, CodedIndex.hasConstant(MetadataTokens.fieldDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x04 or 1, CodedIndex.hasConstant(MetadataTokens.parameterHandle(1).toEntityHandle()))
        assertEquals(0x04 or 2, CodedIndex.hasConstant(MetadataTokens.propertyDefinitionHandle(1).toEntityHandle()))
    }

    @Test
    fun customAttributeType() {
        assertEquals(2, CodedIndex.customAttributeType(MetadataTokens.methodDefinitionHandle(0).toEntityHandle()))
        assertEquals(
            (0xffffff shl 3) or 3,
            CodedIndex.customAttributeType(MetadataTokens.memberReferenceHandle(0xffffff).toEntityHandle()),
        )
        assertEquals(0x08 or 2, CodedIndex.customAttributeType(MetadataTokens.methodDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x08 or 3, CodedIndex.customAttributeType(MetadataTokens.memberReferenceHandle(1).toEntityHandle()))
    }

    @Test
    fun hasDeclSecurity() {
        assertEquals(0, CodedIndex.hasDeclSecurity(MetadataTokens.typeDefinitionHandle(0).toEntityHandle()))
        assertEquals(
            (0xffffff shl 2) or 1,
            CodedIndex.hasDeclSecurity(MetadataTokens.methodDefinitionHandle(0xffffff).toEntityHandle()),
        )
        assertEquals(0x04 or 0, CodedIndex.hasDeclSecurity(MetadataTokens.typeDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x04 or 1, CodedIndex.hasDeclSecurity(MetadataTokens.methodDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x04 or 2, CodedIndex.hasDeclSecurity(EntityHandle.ASSEMBLY_DEFINITION))
    }

    @Test
    fun hasFieldMarshal_andMemberForwarded() {
        assertEquals(0x02 or 0, CodedIndex.hasFieldMarshal(MetadataTokens.fieldDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x02 or 1, CodedIndex.hasFieldMarshal(MetadataTokens.parameterHandle(1).toEntityHandle()))
        assertEquals(
            (0xffffff shl 1) or 1,
            CodedIndex.hasFieldMarshal(MetadataTokens.parameterHandle(0xffffff).toEntityHandle()),
        )

        assertEquals(0x02 or 0, CodedIndex.memberForwarded(MetadataTokens.fieldDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x02 or 1, CodedIndex.memberForwarded(MetadataTokens.methodDefinitionHandle(1).toEntityHandle()))
    }

    @Test
    fun implementation_memberRefParent_others() {
        assertEquals(0x04 or 0, CodedIndex.implementation(MetadataTokens.assemblyFileHandle(1).toEntityHandle()))
        assertEquals(0x04 or 1, CodedIndex.implementation(MetadataTokens.assemblyReferenceHandle(1).toEntityHandle()))
        assertEquals(0x04 or 2, CodedIndex.implementation(MetadataTokens.exportedTypeHandle(1).toEntityHandle()))

        assertEquals(0x08 or 0, CodedIndex.memberRefParent(MetadataTokens.typeDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x08 or 1, CodedIndex.memberRefParent(MetadataTokens.typeReferenceHandle(1).toEntityHandle()))
        assertEquals(0x08 or 2, CodedIndex.memberRefParent(MetadataTokens.moduleReferenceHandle(1).toEntityHandle()))
        assertEquals(0x08 or 3, CodedIndex.memberRefParent(MetadataTokens.methodDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x08 or 4, CodedIndex.memberRefParent(MetadataTokens.typeSpecificationHandle(1).toEntityHandle()))

        assertEquals(0x02 or 0, CodedIndex.methodDefOrRef(MetadataTokens.methodDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x02 or 1, CodedIndex.methodDefOrRef(MetadataTokens.memberReferenceHandle(1).toEntityHandle()))

        assertEquals(0x04 or 0, CodedIndex.resolutionScope(EntityHandle.MODULE_DEFINITION))
        assertEquals(0x04 or 1, CodedIndex.resolutionScope(MetadataTokens.moduleReferenceHandle(1).toEntityHandle()))
        assertEquals(0x04 or 2, CodedIndex.resolutionScope(MetadataTokens.assemblyReferenceHandle(1).toEntityHandle()))
        assertEquals(0x04 or 3, CodedIndex.resolutionScope(MetadataTokens.typeReferenceHandle(1).toEntityHandle()))

        assertEquals(0x04 or 0, CodedIndex.typeDefOrRef(MetadataTokens.typeDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x04 or 1, CodedIndex.typeDefOrRef(MetadataTokens.typeReferenceHandle(1).toEntityHandle()))

        assertEquals(0x04 or 0, CodedIndex.typeDefOrRefOrSpec(MetadataTokens.typeDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x04 or 1, CodedIndex.typeDefOrRefOrSpec(MetadataTokens.typeReferenceHandle(1).toEntityHandle()))
        assertEquals(0x04 or 2, CodedIndex.typeDefOrRefOrSpec(MetadataTokens.typeSpecificationHandle(1).toEntityHandle()))

        assertEquals(0x02 or 0, CodedIndex.typeOrMethodDef(MetadataTokens.typeDefinitionHandle(1).toEntityHandle()))
        assertEquals(0x02 or 1, CodedIndex.typeOrMethodDef(MetadataTokens.methodDefinitionHandle(1).toEntityHandle()))
    }

    // --- MetadataTokens ---

    private val virtualAssemblyRef: EntityHandle =
        EntityHandle(TokenTypeIds.VIRTUAL_BIT or TokenTypeIds.ASSEMBLY_REF or 1u)

    @Test
    fun getRowNumber() {
        assertEquals(1, MetadataTokens.getRowNumber(MetadataTokens.assemblyReferenceHandle(1).toEntityHandle()))
        assertEquals(-1, MetadataTokens.getRowNumber(virtualAssemblyRef))
    }

    @Test
    fun getHeapOffset_variants() {
        assertEquals(-1, MetadataTokens.getHeapOffset(StringHandle.fromOffset(123).withWinRTPrefix()))
        assertEquals(123, MetadataTokens.getHeapOffset(StringHandle.fromOffset(123)))

        assertEquals(1, MetadataTokens.getHeapOffset(UserStringHandle.fromOffset(1)))
        assertEquals(1, MetadataTokens.getHeapOffset(BlobHandle.fromOffset(1)))
        assertEquals(5, MetadataTokens.getHeapOffset(GuidHandle.fromIndex(5)))

        val heap = Handle(HandleType.USER_STRING.toUByte(), 7)
        assertEquals(7, MetadataTokens.getHeapOffset(heap))
        assertFailsWith<IllegalStateException> { MetadataTokens.getHeapOffset(Handle(HandleType.TYPE_DEF.toUByte(), 7)) }
    }

    @Test
    fun getToken_variants() {
        val assemblyRef: EntityHandle = MetadataTokens.assemblyReferenceHandle(1).toEntityHandle()
        assertEquals(0x23000001, MetadataTokens.getToken(assemblyRef))
        assertEquals(0x23000001, MetadataTokens.getToken(assemblyRef.toHandle()))
        assertEquals(0x70000001, MetadataTokens.getToken(UserStringHandle.fromOffset(1).toHandle()))
        // blob handles carry no tokens (same as the original)
        assertFailsWith<IllegalStateException> { MetadataTokens.getToken(BlobHandle.fromOffset(1).toHandle()) }
        assertEquals(0, MetadataTokens.getToken(virtualAssemblyRef))
    }

    @Test
    fun handleFactories_roundTrip() {
        val h = MetadataTokens.handle(0x23000001)
        assertEquals(HandleKind.ASSEMBLY_REFERENCE, h.kind)
        assertEquals(1, h.rowId)

        assertEquals(h.toEntityHandle(), MetadataTokens.entityHandle(0x23000001))
        assertEquals(h.toEntityHandle(), MetadataTokens.entityHandle(TableIndex.ASSEMBLY_REF, 1))
        assertEquals(h.toEntityHandle(), MetadataTokens.handle(TableIndex.ASSEMBLY_REF, 1))

        assertFailsWith<IllegalStateException> { MetadataTokens.entityHandle(0x70000001) } // user string token
        assertFailsWith<IllegalStateException> { MetadataTokens.handle(0xFF000001.toInt()) } // no such table (0x7F)
    }

    @Test
    fun tryGetIndexes() {
        assertEquals(TableIndex.ASSEMBLY_REF, MetadataTokens.tryGetTableIndex(HandleKind.ASSEMBLY_REFERENCE))
        assertNull(MetadataTokens.tryGetTableIndex(HandleKind.BLOB))
        assertEquals(TableIndex.MODULE, MetadataTokens.tryGetTableIndex(HandleKind.MODULE_DEFINITION))
        assertEquals(TableIndex.TYPE_DEF, MetadataTokens.tryGetTableIndex(HandleKind.TYPE_DEFINITION))

        assertEquals(HeapIndex.USER_STRING, MetadataTokens.tryGetHeapIndex(HandleKind.USER_STRING))
        assertEquals(HeapIndex.STRING, MetadataTokens.tryGetHeapIndex(HandleKind.NAMESPACE_DEFINITION))
        assertEquals(HeapIndex.BLOB, MetadataTokens.tryGetHeapIndex(HandleKind.BLOB))
        assertEquals(HeapIndex.GUID, MetadataTokens.tryGetHeapIndex(HandleKind.GUID))
        assertNull(MetadataTokens.tryGetHeapIndex(HandleKind.TYPE_DEFINITION))
    }

    // --- ILOpCodeExtensions ---

    @Test
    fun branchClassification() {
        assertTrue(ILOpCode.BR.isBranch())
        assertTrue(ILOpCode.LEAVE_S.isBranch())
        assertFalse(ILOpCode.CALL.isBranch())
        assertFalse(ILOpCode.RET.isBranch())

        assertEquals(1, ILOpCode.BR_S.getBranchOperandSize())
        assertEquals(4, ILOpCode.BR.getBranchOperandSize())
        assertEquals(4, ILOpCode.BGT_UN.getBranchOperandSize())
        assertFailsWith<IllegalArgumentException> { ILOpCode.NOP.getBranchOperandSize() }
    }

    @Test
    fun shortAndLongBranchForms() {
        assertEquals(ILOpCode.BR_S, ILOpCode.BR.getShortBranch())
        assertEquals(ILOpCode.BLT_UN_S, ILOpCode.BLT_UN.getShortBranch())
        assertEquals(ILOpCode.BR, ILOpCode.BR_S.getLongBranch())
        assertEquals(ILOpCode.BLT_UN, ILOpCode.BLT_UN_S.getLongBranch())
        assertEquals(ILOpCode.BR_S, ILOpCode.BR_S.getShortBranch())
        assertEquals(ILOpCode.BR, ILOpCode.BR.getLongBranch())
        assertFailsWith<IllegalArgumentException> { ILOpCode.NOP.getShortBranch() }
        assertFailsWith<IllegalArgumentException> { ILOpCode.NOP.getLongBranch() }
    }
}
