package com.togethertrip.main.post.dto.response

import com.togethertrip.main.post.domain.PostAttachment
import com.togethertrip.main.post.domain.PostAttachmentType

data class PostAttachmentResponse(
    val id: Long,
    val attachmentType: PostAttachmentType,
    val fileUrl: String,
    val thumbnailUrl: String?,
    val fileSize: Long?,
    val mimeType: String?,
    val sortOrder: Int,
) {
    companion object {
        fun from(attachment: PostAttachment): PostAttachmentResponse {
            return PostAttachmentResponse(
                id = attachment.id,
                attachmentType = attachment.attachmentType,
                fileUrl = attachment.fileUrl,
                thumbnailUrl = attachment.thumbnailUrl,
                fileSize = attachment.fileSize,
                mimeType = attachment.mimeType,
                sortOrder = attachment.sortOrder,
            )
        }
    }
}
