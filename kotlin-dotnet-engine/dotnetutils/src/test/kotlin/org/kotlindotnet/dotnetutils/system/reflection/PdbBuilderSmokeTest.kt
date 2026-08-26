package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.*
import kotlin.uuid.Uuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Самосогласованность standalone-образа через публичный [PdbBuilder]. */
class PdbBuilderSmokeTest {
    @Test
    fun `standalone pdb built by PdbBuilder is self-consistent`() {
        val m = MetadataBuilder()
        val nameBlob = BlobBuilder().also {
            it.writeByte(9)
            it.writeBytes("/tmp/x.kt".toByteArray())
        }
        val doc = m.addDocument(
            name = m.getOrAddBlob(nameBlob),
            hashAlgorithm = m.getOrAddGuid(Uuid.fromLongs(1L, 2L)),
            hash = m.getOrAddBlob(BlobBuilder().also { it.writeBytes(ByteArray(32)) }),
            language = m.getOrAddGuid(Uuid.fromLongs(3L, 4L)),
        )
        assertEquals(1, doc.rowId)
        m.addMethodDebugInformation(doc, m.getOrAddBlob(BlobBuilder()))

        val image = PdbBuilder(m, id = ByteArray(16) { it.toByte() }, entryPointToken = 0x6000001).build()

        assertTrue(image.count > 84, "image must contain root + heaps + #Pdb stream")
        assertEquals(0, image.count % 4)
    }
}
