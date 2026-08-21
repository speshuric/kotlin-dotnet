// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/PEFileFlags.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

enum class Characteristics(val value: Int) {
    RELOCS_STRIPPED(0x0001),        // Relocation info stripped from file.
    EXECUTABLE_IMAGE(0x0002),       // File is executable (i.e. no unresolved external references).
    LINE_NUMS_STRIPPED(0x0004),     // Line numbers stripped from file.
    LOCAL_SYMS_STRIPPED(0x0008),    // Local symbols stripped from file.
    AGGRESSIVE_WS_TRIM(0x0010),     // Aggressively trim working set
    LARGE_ADDRESS_AWARE(0x0020),    // App can handle >2gb addresses
    BYTES_REVERSED_LO(0x0080),      // Bytes of machine word are reversed.
    BIT_32_MACHINE(0x0100),         // 32 bit word machine.
    DEBUG_STRIPPED(0x0200),         // Debugging info stripped from file in .DBG file
    REMOVABLE_RUN_FROM_SWAP(0x0400),// If Image is on removable media, copy and run from the swap file.
    NET_RUN_FROM_SWAP(0x0800),      // If Image is on Net, copy and run from the swap file.
    SYSTEM(0x1000),                 // System File.
    DLL(0x2000),                    // File is a DLL.
    UP_SYSTEM_ONLY(0x4000),         // File should only be run on a UP machine
    BYTES_REVERSED_HI(0x8000),      // Bytes of machine word are reversed.
}

enum class PEMagic(val value: Int) {
    PE32(0x010B),
    PE32PLUS(0x020B),
}

enum class Subsystem(val value: Int) {
    UNKNOWN(0),                     // Unknown subsystem.
    NATIVE(1),                      // Image doesn't require a subsystem.
    WINDOWS_GUI(2),                 // Image runs in the Windows GUI subsystem.
    WINDOWS_CUI(3),                 // Image runs in the Windows character subsystem.
    OS2_CUI(5),                     // image runs in the OS/2 character subsystem.
    POSIX_CUI(7),                   // image runs in the Posix character subsystem.
    NATIVE_WINDOWS(8),              // image is a native Win9x driver.
    WINDOWS_CE_GUI(9),              // Image runs in the Windows CE subsystem.
    EFI_APPLICATION(10),            // Extensible Firmware Interface (EFI) application.
    EFI_BOOT_SERVICE_DRIVER(11),    // EFI driver with boot services.
    EFI_RUNTIME_DRIVER(12),         // EFI driver with run-time services.
    EFI_ROM(13),                    // EFI ROM image.
    XBOX(14),                       // XBox system.
    WINDOWS_BOOT_APPLICATION(16),   // Boot application.
}

enum class DllCharacteristics(val value: Int) {
    PROCESS_INIT(0x0001),           // Reserved.
    PROCESS_TERM(0x0002),           // Reserved.
    THREAD_INIT(0x0004),            // Reserved.
    THREAD_TERM(0x0008),            // Reserved.
    HIGH_ENTROPY_VIRTUAL_ADDRESS_SPACE(0x0020), // Image can handle a high entropy 64-bit virtual address space.
    DYNAMIC_BASE(0x0040),           // DLL can move.
    FORCE_INTEGRITY(0x0080),        // Code integrity checks are enforced.
    NX_COMPATIBLE(0x0100),          // Image is NX compatible.
    NO_ISOLATION(0x0200),           // Image understands isolation and doesn't want it.
    NO_SEH(0x0400),                 // Image does not use SEH. No SE handler may reside in this image.
    NO_BIND(0x0800),                // Do not bind this image.
    APP_CONTAINER(0x1000),          // The image must run inside an AppContainer.
    WDM_DRIVER(0x2000),             // Driver uses WDM model.
    CONTROL_FLOW_GUARD(0x4000),     // The image supports Control Flow Guard.
    TERMINAL_SERVER_AWARE(0x8000),  // The image is Terminal Server aware.
}

enum class SectionCharacteristics(val value: UInt) {
    TYPE_REG(0x00000000u),              // Reserved.
    TYPE_D_SECT(0x00000001u),           // Reserved.
    TYPE_NO_LOAD(0x00000002u),          // Reserved.
    TYPE_GROUP(0x00000004u),            // Reserved.
    TYPE_NO_PAD(0x00000008u),           // Reserved.
    TYPE_COPY(0x00000010u),             // Reserved.

    CONTAINS_CODE(0x00000020u),         // Section contains code.
    CONTAINS_INITIALIZED_DATA(0x00000040u),   // Section contains initialized data.
    CONTAINS_UNINITIALIZED_DATA(0x00000080u), // Section contains uninitialized data.

    LINKER_OTHER(0x00000100u),          // Reserved.
    LINKER_INFO(0x00000200u),           // Section contains comments or some other type of information.
    TYPE_OVER(0x00000400u),             // Reserved.
    LINKER_REMOVE(0x00000800u),         // Section contents will not become part of image.
    LINKER_COMDAT(0x00001000u),         // Section contents comdat.
    MEM_PROTECTED(0x00004000u),
    NO_DEFER_SPEC_EXC(0x00004000u),     // Reset speculative exceptions handling bits in the TLB entries for this section.
    GP_REL(0x00008000u),                // Section content can be accessed relative to GP
    MEM_FARDATA(0x00008000u),
    MEM_SYS_HEAP(0x00010000u),
    MEM_PURGEABLE(0x00020000u),
    MEM_16BIT(0x00020000u),
    MEM_LOCKED(0x00040000u),
    MEM_PRELOAD(0x00080000u),

    ALIGN_1_BYTES(0x00100000u),
    ALIGN_2_BYTES(0x00200000u),
    ALIGN_4_BYTES(0x00300000u),
    ALIGN_8_BYTES(0x00400000u),
    ALIGN_16_BYTES(0x00500000u),        // Default alignment if no others are specified.
    ALIGN_32_BYTES(0x00600000u),
    ALIGN_64_BYTES(0x00700000u),
    ALIGN_128_BYTES(0x00800000u),
    ALIGN_256_BYTES(0x00900000u),
    ALIGN_512_BYTES(0x00A00000u),
    ALIGN_1024_BYTES(0x00B00000u),
    ALIGN_2048_BYTES(0x00C00000u),
    ALIGN_4096_BYTES(0x00D00000u),
    ALIGN_8192_BYTES(0x00E00000u),
    ALIGN_MASK(0x00F00000u),

    LINKER_N_RELOC_OVFL(0x01000000u),   // Section contains extended relocations.
    MEM_DISCARDABLE(0x02000000u),       // Section can be discarded.
    MEM_NOT_CACHED(0x04000000u),        // Section is not cachable.
    MEM_NOT_PAGED(0x08000000u),         // Section is not pageable.
    MEM_SHARED(0x10000000u),            // Section is shareable.
    MEM_EXECUTE(0x20000000u),           // Section is executable.
    MEM_READ(0x40000000u),              // Section is readable.
    MEM_WRITE(0x80000000u),             // Section is writable.
}
