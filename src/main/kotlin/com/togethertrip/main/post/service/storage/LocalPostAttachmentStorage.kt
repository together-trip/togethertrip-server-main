package com.togethertrip.main.post.service.storage

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.post.domain.PostAttachmentType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.extension

@Component
class LocalPostAttachmentStorage(
    @Value("\${post.attachments.local-storage-path:./uploads/post-attachments}")
    private val storagePath: String,
    @Value("\${post.attachments.public-url-prefix:/uploads/post-attachments}")
    private val publicUrlPrefix: String,
) : PostAttachmentStorage {

    override fun store(file: MultipartFile): StoredPostAttachment {
        if (file.isEmpty) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        val attachmentType = resolveAttachmentType(file.contentType)
        val storedFileName = createStoredFileName(file.originalFilename)
        val storageDirectory = Path.of(storagePath).toAbsolutePath().normalize()
        val targetPath = storageDirectory.resolve(storedFileName).normalize()

        if (!targetPath.startsWith(storageDirectory)) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        Files.createDirectories(storageDirectory)
        file.inputStream.use { inputStream ->
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
            mimeType = file.contentType,
        )
    }

    override fun delete(storedAttachment: StoredPostAttachment) {
        val storageDirectory = Path.of(storagePath).toAbsolutePath().normalize()
        val targetPath = storageDirectory.resolve(storedAttachment.storageKey).normalize()

        if (targetPath.startsWith(storageDirectory)) {
            Files.deleteIfExists(targetPath)
        }
    }

    private fun resolveAttachmentType(contentType: String?): PostAttachmentType {
        return when {
            contentType?.startsWith("image/") == true -> PostAttachmentType.IMAGE
            contentType?.startsWith("video/") == true -> PostAttachmentType.VIDEO
            else -> throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun createStoredFileName(originalFilename: String?): String {
        val extension = originalFilename
            ?.let { Path.of(it).fileName }
            ?.extension
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

        return if (extension == null) {
            UUID.randomUUID().toString()
        } else {
            "${UUID.randomUUID()}.$extension"
        }
    }
}
