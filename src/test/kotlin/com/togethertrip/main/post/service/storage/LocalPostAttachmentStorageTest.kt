package com.togethertrip.main.post.service.storage

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.storage.UploadFileTypeDetector
import com.togethertrip.main.post.domain.PostAttachmentType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalPostAttachmentStorageTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `multipart 이미지 파일을 로컬 디스크에 저장하고 서버 생성 URL을 반환한다`() {
        val storage = LocalPostAttachmentStorage(
            storagePath = tempDir.toString(),
            publicUrlPrefix = "/uploads/post-attachments",
            uploadFileTypeDetector = UploadFileTypeDetector(),
        )
        val file = MockMultipartFile(
            "files",
            "receipt.JPG",
            "image/jpeg",
            jpegBytes(),
        )

        val stored = storage.store(file)

        assertEquals(PostAttachmentType.IMAGE, stored.attachmentType)
        assertTrue(stored.storageKey.endsWith(".jpg"))
        assertEquals("image/jpeg", stored.mimeType)
        assertEquals(file.size, stored.fileSize)
        assertTrue(stored.fileUrl.startsWith("/uploads/post-attachments/"))
        assertTrue(stored.fileUrl.endsWith(".jpg"))
        assertEquals(1, Files.list(tempDir).use { it.count() })

        storage.delete(stored)

        assertEquals(0, Files.list(tempDir).use { it.count() })
    }

    @Test
    fun `multipart 영상 파일을 로컬 디스크에 저장하고 서버 생성 URL을 반환한다`() {
        val storage = LocalPostAttachmentStorage(
            storagePath = tempDir.toString(),
            publicUrlPrefix = "/uploads/post-attachments",
            uploadFileTypeDetector = UploadFileTypeDetector(),
        )
        val file = MockMultipartFile(
            "files",
            "clip.mov",
            "video/quicktime",
            mp4Bytes(),
        )

        val stored = storage.store(file)

        assertEquals(PostAttachmentType.VIDEO, stored.attachmentType)
        assertTrue(stored.storageKey.endsWith(".mp4"))
        assertEquals("video/mp4", stored.mimeType)
        assertTrue(stored.fileUrl.endsWith(".mp4"))
        assertEquals(1, Files.list(tempDir).use { it.count() })
    }

    @Test
    fun `지원하지 않는 파일 시그니처이면 실패한다`() {
        val storage = LocalPostAttachmentStorage(
            storagePath = tempDir.toString(),
            publicUrlPrefix = "/uploads/post-attachments",
            uploadFileTypeDetector = UploadFileTypeDetector(),
        )
        val file = MockMultipartFile(
            "files",
            "memo.txt",
            "image/jpeg",
            "plain-text".toByteArray(),
        )

        assertFailsWith<BusinessException> {
            storage.store(file)
        }
    }

    private fun jpegBytes(): ByteArray {
        return byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            0xE0.toByte(),
            0x00,
            0x10,
        )
    }

    private fun mp4Bytes(): ByteArray {
        return byteArrayOf(
            0x00,
            0x00,
            0x00,
            0x18,
            0x66,
            0x74,
            0x79,
            0x70,
            0x6D,
            0x70,
            0x34,
            0x32,
        )
    }
}
