package com.togethertrip.main.post.service.storage

import com.togethertrip.main.post.domain.PostAttachmentType
import org.springframework.web.multipart.MultipartFile

interface PostAttachmentStorage {

    fun store(file: MultipartFile): StoredPostAttachment

    fun delete(storedAttachment: StoredPostAttachment)
}

data class StoredPostAttachment(
    val storageKey: String,
    val attachmentType: PostAttachmentType,
    val fileUrl: String,
    val thumbnailUrl: String?,
    val fileSize: Long?,
    val mimeType: String?,
)
