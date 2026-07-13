package com.togethertrip.main.triprecap.service.storage

import com.togethertrip.main.global.storage.UploadFileTypeDetector
import com.togethertrip.main.triprecap.service.ai.TripRecapPhotoReference
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalTripRecapPhotoContentLoaderTest {

    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `loads an uploaded PNG from configured local storage`() {
        val bytes = pngBytes()
        Files.write(tempDirectory.resolve("reference.png"), bytes)
        val loader = loader(maxBytes = 1024)

        val content = loader.load(
            TripRecapPhotoReference(
                imageUrl = "/uploads/post-attachments/reference.png",
                thumbnailUrl = null,
            )
        )

        assertEquals("reference.png", content?.filename)
        assertEquals("image/png", content?.contentType)
        assertContentEquals(bytes, content?.bytes)
    }

    @Test
    fun `rejects paths outside configured upload prefix`() {
        val loader = loader(maxBytes = 1024)

        val content = loader.load(
            TripRecapPhotoReference(
                imageUrl = "/uploads/post-attachments/../secret.png",
                thumbnailUrl = null,
            )
        )

        assertNull(content)
    }

    @Test
    fun `skips reference images larger than configured limit`() {
        Files.write(tempDirectory.resolve("large.png"), pngBytes() + ByteArray(32))
        val loader = loader(maxBytes = 8)

        val content = loader.load(
            TripRecapPhotoReference(
                imageUrl = "/uploads/post-attachments/large.png",
                thumbnailUrl = null,
            )
        )

        assertNull(content)
    }

    private fun loader(maxBytes: Long): LocalTripRecapPhotoContentLoader {
        return LocalTripRecapPhotoContentLoader(
            storagePath = tempDirectory.toString(),
            publicUrlPrefix = "/uploads/post-attachments",
            maxReferenceImageBytes = maxBytes,
            uploadFileTypeDetector = UploadFileTypeDetector(),
        )
    }

    private fun pngBytes(): ByteArray {
        return byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}
