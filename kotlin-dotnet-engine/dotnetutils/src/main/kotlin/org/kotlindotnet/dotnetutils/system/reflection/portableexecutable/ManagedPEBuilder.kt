// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/ManagedPEBuilder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - DebugDirectoryBuilder is not ported (deferred, see REMAINING-ROADMAP.md
//    §D); the debugDirectoryBuilder parameter and the reproducible-entry
//    fallback are cut — debug data size is always 0;
//  - flags parameter is a raw Int ([Flags] enum CorFlags);
//  - Machine.UNKNOWN stands in for C# literal 0.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

import org.kotlindotnet.dotnetutils.system.reflection.metadata.Blob
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobContentId
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataTokens
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataRootBuilder

open class ManagedPEBuilder(
    header: PEHeaderBuilder,
    private val metadataRootBuilder: MetadataRootBuilder,
    private val ilStream: BlobBuilder,
    private val mappedFieldData: BlobBuilder? = null,
    private val managedResources: BlobBuilder? = null,
    private val nativeResources: ResourceSectionBuilder? = null,
    private val strongNameSignatureSize: Int = DEFAULT_STRONG_NAME_SIGNATURE_SIZE,
    private val entryPoint: MethodDefinitionHandle = MetadataTokens.methodDefinitionHandle(0),
    flags: Int = CorFlags.IL_ONLY.value,
    deterministicIdProvider: ((Iterable<Blob>) -> BlobContentId)? = null,
) : PEBuilder(header, deterministicIdProvider) {
    private val corFlags: Int = flags

    private val peDirectoriesBuilder: PEDirectoriesBuilder = PEDirectoriesBuilder()

    private var lazyEntryPointAddress: Int = 0
    private var lazyStrongNameSignature: Blob = Blob.DEFAULT

    init {
        require(strongNameSignatureSize >= 0) { "strongNameSignatureSize out of range" }
    }

    override fun createSections(): List<Section> {
        val builder = ArrayList<Section>(3)
        builder.add(
            Section(
                TEXT_SECTION_NAME,
                SectionCharacteristics.MEM_READ.value or
                    SectionCharacteristics.MEM_EXECUTE.value or
                    SectionCharacteristics.CONTAINS_CODE.value,
            ),
        )

        if (nativeResources != null) {
            builder.add(
                Section(
                    RESOURCE_SECTION_NAME,
                    SectionCharacteristics.MEM_READ.value or
                        SectionCharacteristics.CONTAINS_INITIALIZED_DATA.value,
                ),
            )
        }

        if (header.machine == Machine.I386 || header.machine == Machine.UNKNOWN) {
            builder.add(
                Section(
                    RELOCATION_SECTION_NAME,
                    SectionCharacteristics.MEM_READ.value or
                        SectionCharacteristics.MEM_DISCARDABLE.value or
                        SectionCharacteristics.CONTAINS_INITIALIZED_DATA.value,
                ),
            )
        }

        return builder
    }

    override fun serializeSection(name: String, location: SectionLocation): BlobBuilder =
        when (name) {
            TEXT_SECTION_NAME -> serializeTextSection(location)
            RESOURCE_SECTION_NAME -> serializeResourceSection(location)
            RELOCATION_SECTION_NAME -> serializeRelocationSection(location)
            else -> throw IllegalArgumentException("Unknown section name: $name")
        }

    private fun serializeTextSection(location: SectionLocation): BlobBuilder {
        val sectionBuilder = BlobBuilder()
        val metadataBuilder = BlobBuilder()

        val metadataSizes = metadataRootBuilder.sizes

        val textSection =
            ManagedTextSection(
                imageCharacteristics = header.imageCharacteristics,
                machine = header.machine,
                ilStreamSize = ilStream.count,
                metadataSize = metadataSizes.metadataSize,
                resourceDataSize = managedResources?.count ?: 0,
                strongNameSignatureSize = strongNameSignatureSize,
                debugDataSize = 0,
                mappedFieldDataSize = mappedFieldData?.count ?: 0,
            )

        val methodBodyStreamRva = location.relativeVirtualAddress + textSection.offsetToILStream
        val mappedFieldDataStreamRva =
            location.relativeVirtualAddress + textSection.calculateOffsetToMappedFieldDataStream()
        metadataRootBuilder.serialize(metadataBuilder, methodBodyStreamRva, mappedFieldDataStreamRva)

        lazyEntryPointAddress = textSection.getEntryPointAddress(location.relativeVirtualAddress)

        lazyStrongNameSignature =
            textSection.serialize(
                sectionBuilder,
                location.relativeVirtualAddress,
                if (entryPoint.isNil) 0 else MetadataTokens.getToken(entryPoint.toEntityHandle()),
                corFlags,
                header.imageBase,
                metadataBuilder,
                ilStream,
                mappedFieldData,
                managedResources,
                null,
            )

        peDirectoriesBuilder.addressOfEntryPoint = lazyEntryPointAddress
        peDirectoriesBuilder.debugTable = DirectoryEntry(0, 0)
        peDirectoriesBuilder.importAddressTable =
            textSection.getImportAddressTableDirectoryEntry(location.relativeVirtualAddress)
        peDirectoriesBuilder.importTable = textSection.getImportTableDirectoryEntry(location.relativeVirtualAddress)
        peDirectoriesBuilder.corHeaderTable = textSection.getCorHeaderDirectoryEntry(location.relativeVirtualAddress)

        return sectionBuilder
    }

    private fun serializeResourceSection(location: SectionLocation): BlobBuilder {
        checkNotNull(nativeResources) { "no native resources" }

        val sectionBuilder = BlobBuilder()
        nativeResources.serialize(sectionBuilder, location)

        peDirectoriesBuilder.resourceTable = DirectoryEntry(location.relativeVirtualAddress, sectionBuilder.count)
        return sectionBuilder
    }

    private fun serializeRelocationSection(location: SectionLocation): BlobBuilder {
        val sectionBuilder = BlobBuilder()
        writeRelocationSection(sectionBuilder, header.machine, lazyEntryPointAddress)

        peDirectoriesBuilder.baseRelocationTable = DirectoryEntry(location.relativeVirtualAddress, sectionBuilder.count)
        return sectionBuilder
    }

    override fun getDirectories(): PEDirectoriesBuilder = peDirectoriesBuilder

    fun sign(peImage: BlobBuilder, signatureProvider: (Iterable<Blob>) -> ByteArray) {
        sign(peImage, lazyStrongNameSignature, signatureProvider)
    }

    companion object {
        const val MANAGED_RESOURCES_DATA_ALIGNMENT = ManagedTextSection.MANAGED_RESOURCES_DATA_ALIGNMENT
        const val MAPPED_FIELD_DATA_ALIGNMENT = ManagedTextSection.MAPPED_FIELD_DATA_ALIGNMENT

        private const val DEFAULT_STRONG_NAME_SIGNATURE_SIZE = 128

        private const val TEXT_SECTION_NAME = ".text"
        private const val RESOURCE_SECTION_NAME = ".rsrc"
        private const val RELOCATION_SECTION_NAME = ".reloc"

        private fun writeRelocationSection(builder: BlobBuilder, machine: Machine, entryPointAddress: Int) {
            builder.writeUInt32((((entryPointAddress.toUInt() + 2u) / 0x1000u) * 0x1000u))
            builder.writeUInt32(if (machine == Machine.IA64) 14u else 12u)
            val offsetWithinPage = (entryPointAddress.toUInt() + 2u) % 0x1000u
            val relocType =
                (if (machine == Machine.AMD64 || machine == Machine.IA64 || machine == Machine.ARM64) 10u else 3u)
            val s = ((relocType shl 12) or offsetWithinPage).toUShort()
            builder.writeUInt16(s)
            if (machine == Machine.IA64) {
                builder.writeUInt32(relocType shl 12)
            }

            builder.writeUInt16(0.toUShort()) // next chunk's RVA
        }
    }
}
