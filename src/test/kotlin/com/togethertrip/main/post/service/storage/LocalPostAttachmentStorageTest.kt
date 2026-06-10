package com.togethertrip.main.post.service.storage

import com.togethertrip.main.global.exception.BusinessException
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
        )
        val file = MockMultipartFile(
            "files",
            "receipt.JPG",
            "image/jpeg",
            "image-content".toByteArray(),
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
    fun `지원하지 않는 content type이면 실패한다`() {
        val storage = LocalPostAttachmentStorage(
            storagePath = tempDir.toString(),
            publicUrlPrefix = "/uploads/post-attachments",
        )
        val file = MockMultipartFile(
            "files",
            "memo.txt",
            "text/plain",
            "plain-text".toByteArray(),
        )

        assertFailsWith<BusinessException> {
            storage.store(file)
        }
    }
}
