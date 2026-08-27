package org.kotlindotnet.compiler.pe

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataRootBuilder
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.Characteristics
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.CorFlags
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.ManagedPEBuilder
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.PEHeaderBuilder
import java.io.File

/**
 * Final image serialization: metadata root + IL stream → a PE file
 * (.exe/.dll). The only place that knows about PE headers.
 */
internal object PeImageWriter {

    fun serialize(
        metadata: MetadataBuilder,
        ilStream: BlobBuilder,
        entrypoint: MethodDefinitionHandle,
        outputFile: File,
        isExe: Boolean,
    ) {
        val imageCharacteristics =
            Characteristics.EXECUTABLE_IMAGE.value or
                (if (!isExe) Characteristics.DLL.value else 0)

        val header = PEHeaderBuilder(imageCharacteristics = imageCharacteristics)
        val peBuilder =
            ManagedPEBuilder(
                header = header,
                metadataRootBuilder = MetadataRootBuilder(metadata),
                ilStream = ilStream,
                entryPoint = entrypoint,
                flags = CorFlags.IL_ONLY.value,
            )

        val peBlob = BlobBuilder()
        peBuilder.serialize(peBlob)

        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(peBlob.toArray())
    }
}
