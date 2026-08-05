package com.togethertrip.main.post.service.storage

import com.togethertrip.main.post.domain.PostAttachmentType
import com.togethertrip.main.post.domain.PostAttachment
import org.springframework.web.multipart.MultipartFile

interface PostAttachmentStorage {

    fun store(file: MultipartFile): StoredPostAttachment

    fun delete(storedAttachment: StoredPostAttachment)

    fun load(attachment: PostAttachment): StoredPostAttachmentFile
}

data class StoredPostAttachment(
    val storageKey: String,
    val attachmentType: PostAttachmentType,
    val fileUrl: String,
    val thumbnailUrl: String?,
    val fileSize: Long?,
    val mimeType: String?,
)

data class StoredPostAttachmentFile(
    val bytes: ByteArray,
    val contentType: String,
)
