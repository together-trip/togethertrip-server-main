package com.togethertrip.main.user.service.storage

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.storage.UploadFileType
import com.togethertrip.main.global.storage.UploadFileTypeDetector
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

@Component
class LocalUserProfileImageStorage(
    @Value("\${user.profile-images.local-storage-path}")
    private val storagePath: String,
    @Value("\${user.profile-images.public-url-prefix}")
    private val publicUrlPrefix: String,
    private val uploadFileTypeDetector: UploadFileTypeDetector,
) : UserProfileImageStorage {

    override fun store(file: MultipartFile): StoredUserProfileImage {
        if (file.isEmpty) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        val fileBytes = file.bytes
        val imageType = detectProfileImageType(fileBytes)

        val storedFileName = createStoredFileName(imageType)
        val storageDirectory = Path.of(storagePath).toAbsolutePath().normalize()
        val targetPath = storageDirectory.resolve(storedFileName).normalize()

        if (!targetPath.startsWith(storageDirectory)) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        Files.createDirectories(storageDirectory)
        ByteArrayInputStream(fileBytes).use { inputStream ->
            Files.copy(
                inputStream,
                targetPath,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }

        return StoredUserProfileImage(
            storageKey = storedFileName,
            fileUrl = "${publicUrlPrefix.trimEnd('/')}/$storedFileName",
            fileSize = file.size,
            mimeType = imageType.mimeType,
        )
    }

    override fun delete(storedImage: StoredUserProfileImage) {
        val storageDirectory = Path.of(storagePath).toAbsolutePath().normalize()
        val targetPath = storageDirectory.resolve(storedImage.storageKey).normalize()

        if (targetPath.startsWith(storageDirectory)) {
            Files.deleteIfExists(targetPath)
        }
    }

    private fun detectProfileImageType(fileBytes: ByteArray): UploadFileType {
        val fileType = uploadFileTypeDetector.detect(fileBytes)

        if (fileType != UploadFileType.JPEG && fileType != UploadFileType.PNG) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        return fileType
    }

    private fun createStoredFileName(fileType: UploadFileType): String {
        return "${UUID.randomUUID()}.${fileType.extension}"
    }
}
