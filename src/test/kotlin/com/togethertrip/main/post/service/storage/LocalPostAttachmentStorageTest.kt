package com.togethertrip.main.post.service.storage

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.storage.UploadFileTypeDetector
import com.togethertrip.main.post.domain.PostAttachmentType
import com.togethertrip.main.post.domain.PostAttachment
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
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

        val loaded = storage.load(attachment(stored))
        assertEquals(file.bytes.toList(), loaded.bytes.toList())
        assertEquals("image/jpeg", loaded.contentType)

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

    @Test
    fun `UUID와 legacy 단일 파일명을 저장 루트 안에서 로드한다`() {
        val storage = storage()
        val uuidName = "123e4567-e89b-12d3-a456-426614174000.jpg"
        val legacyName = "local-osaka-castle.jpg"
        Files.write(tempDir.resolve(uuidName), jpegBytes())
        Files.write(tempDir.resolve(legacyName), jpegBytes())

        assertEquals(jpegBytes().toList(), storage.load(attachment("/uploads/post-attachments/$uuidName")).bytes.toList())
        assertEquals(jpegBytes().toList(), storage.load(attachment("/uploads/post-attachments/$legacyName")).bytes.toList())
    }

    @Test
    fun `traversal absolute 하위 경로와 backslash를 거부한다`() {
        val storage = storage()
        val invalidUrls = listOf(
            "/uploads/post-attachments/../secret.jpg",
            "/uploads/post-attachments/nested/file.jpg",
            "/uploads/post-attachments/nested\\file.jpg",
            "/tmp/absolute.jpg",
        )

        invalidUrls.forEach { fileUrl ->
            assertFailsWith<BusinessException> { storage.load(attachment(fileUrl)) }
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

    private fun storage() = LocalPostAttachmentStorage(
        storagePath = tempDir.toString(),
        publicUrlPrefix = "/uploads/post-attachments",
        uploadFileTypeDetector = UploadFileTypeDetector(),
    )

    private fun attachment(stored: StoredPostAttachment): PostAttachment = attachment(
        fileUrl = stored.fileUrl,
        attachmentType = stored.attachmentType,
        mimeType = stored.mimeType,
    )

    private fun attachment(
        fileUrl: String,
        attachmentType: PostAttachmentType = PostAttachmentType.IMAGE,
        mimeType: String? = "image/jpeg",
    ): PostAttachment {
        val user = User("작성자")
        val trip = Trip(user, "여행", "KRW")
        val participant = TripParticipant(
            trip, user, user.nickname, participantRole = TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE,
        )
        val post = Post(trip = trip, author = participant, postType = PostType.RECORD)
        return PostAttachment(
            post = post,
            attachmentType = attachmentType,
            fileUrl = fileUrl,
            mimeType = mimeType,
        )
    }
}
