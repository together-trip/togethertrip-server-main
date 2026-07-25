package com.togethertrip.main.post.service.storage

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.storage.UploadFileType
import com.togethertrip.main.global.storage.UploadFileTypeDetector
import com.togethertrip.main.global.storage.UploadMediaKind
import com.togethertrip.main.post.domain.PostAttachmentType
import com.togethertrip.main.post.domain.PostAttachment
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

@Component
class LocalPostAttachmentStorage(
    @Value("\${post.attachments.local-storage-path}")
    private val storagePath: String,
    @Value("\${post.attachments.public-url-prefix}")
    private val publicUrlPrefix: String,
    private val uploadFileTypeDetector: UploadFileTypeDetector,
) : PostAttachmentStorage {

    override fun store(file: MultipartFile): StoredPostAttachment {
        if (file.isEmpty) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        val fileBytes = file.bytes
        val fileType = uploadFileTypeDetector.detect(fileBytes)
            ?: throw BusinessException(CommonErrorCode.INVALID_INPUT)
        val attachmentType = resolveAttachmentType(fileType)
        val storedFileName = createStoredFileName(fileType)
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

        return StoredPostAttachment(
            storageKey = storedFileName,
            attachmentType = attachmentType,
            fileUrl = "${publicUrlPrefix.trimEnd('/')}/$storedFileName",
            thumbnailUrl = null,
            fileSize = file.size,
            mimeType = fileType.mimeType,
        )
    }

    override fun delete(storedAttachment: StoredPostAttachment) {
        val storageDirectory = Path.of(storagePath).toAbsolutePath().normalize()
        val targetPath = storageDirectory.resolve(storedAttachment.storageKey).normalize()

        if (targetPath.startsWith(storageDirectory)) {
            Files.deleteIfExists(targetPath)
        }
    }

    override fun load(attachment: PostAttachment): StoredPostAttachmentFile {
        val storageKey = extractStorageKey(attachment.fileUrl)
        val storageDirectory = Path.of(storagePath).toAbsolutePath().normalize()
        val targetPath = storageDirectory.resolve(storageKey).normalize()
        if (!targetPath.startsWith(storageDirectory) || !Files.isRegularFile(targetPath)) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
        return StoredPostAttachmentFile(
            bytes = Files.readAllBytes(targetPath),
            contentType = attachment.mimeType ?: "application/octet-stream",
        )
    }

    private fun extractStorageKey(fileUrl: String): String {
        val normalizedPrefix = publicUrlPrefix.trimEnd('/')
        val prefixedPath = "$normalizedPrefix/"
        val storageKey = when {
            fileUrl.startsWith(prefixedPath) -> fileUrl.removePrefix(prefixedPath)
            !fileUrl.contains('/') && !fileUrl.contains('\\') -> fileUrl
            else -> throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
        if (!SAFE_BASENAME_PATTERN.matches(storageKey) || Path.of(storageKey).isAbsolute) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
        return storageKey
    }

    private fun resolveAttachmentType(fileType: UploadFileType): PostAttachmentType {
        return when (fileType.mediaKind) {
            UploadMediaKind.IMAGE -> PostAttachmentType.IMAGE
            UploadMediaKind.VIDEO -> PostAttachmentType.VIDEO
        }
    }

    private fun createStoredFileName(fileType: UploadFileType): String {
        return "${UUID.randomUUID()}.${fileType.extension}"
    }

    private companion object {
        val SAFE_BASENAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*\\.(jpg|jpeg|png|mp4)$")
    }
}
