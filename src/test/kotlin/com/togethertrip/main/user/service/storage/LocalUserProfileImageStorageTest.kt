package com.togethertrip.main.user.service.storage

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.storage.UploadFileTypeDetector
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalUserProfileImageStorageTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `multipart 프로필 이미지를 로컬 디스크에 저장하고 서버 생성 URL을 반환한다`() {
        val storage = LocalUserProfileImageStorage(
            storagePath = tempDir.toString(),
            publicUrlPrefix = "/uploads/user-profile-images",
            uploadFileTypeDetector = UploadFileTypeDetector(),
        )
        val file = MockMultipartFile(
            "profileImage",
            "profile.JPG",
            "image/jpeg",
            jpegBytes(),
        )

        val stored = storage.store(file)

        assertTrue(stored.storageKey.endsWith(".jpg"))
        assertEquals("image/jpeg", stored.mimeType)
        assertEquals(file.size, stored.fileSize)
        assertTrue(stored.fileUrl.startsWith("/uploads/user-profile-images/"))
        assertTrue(stored.fileUrl.endsWith(".jpg"))
        assertEquals(1, Files.list(tempDir).use { it.count() })
    }

    @Test
    fun `PNG 프로필 이미지를 로컬 디스크에 저장하고 png URL을 반환한다`() {
        val storage = LocalUserProfileImageStorage(
            storagePath = tempDir.toString(),
            publicUrlPrefix = "/uploads/user-profile-images",
            uploadFileTypeDetector = UploadFileTypeDetector(),
        )
        val file = MockMultipartFile(
            "profileImage",
            "profile.png",
            "image/png",
            pngBytes(),
        )

        val stored = storage.store(file)

        assertTrue(stored.storageKey.endsWith(".png"))
        assertEquals("image/png", stored.mimeType)
        assertTrue(stored.fileUrl.endsWith(".png"))
        assertEquals(1, Files.list(tempDir).use { it.count() })
    }

    @Test
    fun `저장된 프로필 이미지를 삭제한다`() {
        val storage = LocalUserProfileImageStorage(
            storagePath = tempDir.toString(),
            publicUrlPrefix = "/uploads/user-profile-images",
            uploadFileTypeDetector = UploadFileTypeDetector(),
        )
        val file = MockMultipartFile(
            "profileImage",
            "profile.JPG",
            "image/jpeg",
            jpegBytes(),
        )
        val stored = storage.store(file)

        storage.delete(stored)

        assertEquals(0, Files.list(tempDir).use { it.count() })
    }

    @Test
    fun `서버 생성 URL로 저장된 프로필 이미지를 삭제한다`() {
        val storage = LocalUserProfileImageStorage(
            storagePath = tempDir.toString(),
            publicUrlPrefix = "/uploads/user-profile-images",
            uploadFileTypeDetector = UploadFileTypeDetector(),
        )
        val stored = storage.store(
            MockMultipartFile("profileImage", "profile.jpg", "image/jpeg", jpegBytes())
        )

        assertTrue(storage.isManagedFileUrl(stored.fileUrl))
        storage.deleteByFileUrl(stored.fileUrl)

        assertEquals(0, Files.list(tempDir).use { it.count() })
    }

    @Test
    fun `외부 URL과 경로 이탈 URL은 로컬 파일을 삭제하지 않는다`() {
        val storage = LocalUserProfileImageStorage(
            storagePath = tempDir.toString(),
            publicUrlPrefix = "/uploads/user-profile-images",
            uploadFileTypeDetector = UploadFileTypeDetector(),
        )
        storage.store(
            MockMultipartFile("profileImage", "profile.jpg", "image/jpeg", jpegBytes())
        )

        storage.deleteByFileUrl("https://k.kakaocdn.net/profile.jpg")
        storage.deleteByFileUrl("/uploads/user-profile-images/../profile.jpg")

        assertEquals(1, Files.list(tempDir).use { it.count() })
        assertFalse(storage.isManagedFileUrl("https://k.kakaocdn.net/profile.jpg"))
        assertFalse(storage.isManagedFileUrl("/uploads/user-profile-images/../profile.jpg"))
    }

    @Test
    fun `이미지가 아닌 파일이면 실패한다`() {
        val storage = LocalUserProfileImageStorage(
            storagePath = tempDir.toString(),
            publicUrlPrefix = "/uploads/user-profile-images",
            uploadFileTypeDetector = UploadFileTypeDetector(),
        )
        val file = MockMultipartFile(
            "profileImage",
            "memo.txt",
            "image/jpeg",
            "plain-text".toByteArray(),
        )

        assertFailsWith<BusinessException> {
            storage.store(file)
        }
    }

    @Test
    fun `SVG 파일이면 image content type이어도 실패한다`() {
        val storage = LocalUserProfileImageStorage(
            storagePath = tempDir.toString(),
            publicUrlPrefix = "/uploads/user-profile-images",
            uploadFileTypeDetector = UploadFileTypeDetector(),
        )
        val file = MockMultipartFile(
            "profileImage",
            "profile.svg",
            "image/svg+xml",
            "<svg><script>alert(1)</script></svg>".toByteArray(),
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
            0x4A,
            0x46,
            0x49,
            0x46,
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
            0x00,
            0x00,
        )
    }
}
