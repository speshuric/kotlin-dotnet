// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Signatures/SignatureHeader.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - readonly struct -> @JvmInline value class over Byte;
//  - ToString() formatting is simplified.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

/**
 * Represents the signature characteristics specified by the leading byte of
 * signature blobs.
 *
 * This header byte is present in all method definition, method reference,
 * standalone method, field, property, and local variable signatures, but not
 * in type specification signatures.
 */
@JvmInline
value class SignatureHeader(val rawValue: Byte) {
    constructor(
        kind: SignatureKind,
        convention: SignatureCallingConvention,
        attributes: Int = 0,
    ) : this((kind.value or convention.value or attributes).toByte())

    val callingConvention: SignatureCallingConvention
        get() {
            val callingConventionOrKind = rawValue.toInt() and CALLING_CONVENTION_OR_KIND_MASK

            if (callingConventionOrKind > MAX_CALLING_CONVENTION &&
                callingConventionOrKind != SignatureCallingConvention.UNMANAGED.value
            ) {
                return SignatureCallingConvention.DEFAULT
            }

            return SignatureCallingConvention.entries.firstOrNull { it.value == callingConventionOrKind }
                ?: SignatureCallingConvention.DEFAULT
        }

    val kind: SignatureKind
        get() {
            val callingConventionOrKind = rawValue.toInt() and CALLING_CONVENTION_OR_KIND_MASK

            if (callingConventionOrKind <= MAX_CALLING_CONVENTION ||
                callingConventionOrKind == SignatureCallingConvention.UNMANAGED.value
            ) {
                return SignatureKind.METHOD
            }

            return SignatureKind.entries.firstOrNull { it.value == callingConventionOrKind }
                ?: SignatureKind.METHOD
        }

    val attributes: Int
        get() = rawValue.toInt() and CALLING_CONVENTION_OR_KIND_MASK.inv()

    val hasExplicitThis: Boolean
        get() = (rawValue.toInt() and SignatureAttributes.EXPLICIT_THIS.value) != 0

    val isInstance: Boolean
        get() = (rawValue.toInt() and SignatureAttributes.INSTANCE.value) != 0

    val isGeneric: Boolean
        get() = (rawValue.toInt() and SignatureAttributes.GENERIC.value) != 0

    companion object {
        const val CALLING_CONVENTION_OR_KIND_MASK: Int = 0x0F
        private val MAX_CALLING_CONVENTION = SignatureCallingConvention.VAR_ARGS.value
    }
}
