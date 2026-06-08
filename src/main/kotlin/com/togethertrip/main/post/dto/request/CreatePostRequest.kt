package com.togethertrip.main.post.dto.request

import com.togethertrip.main.post.domain.PostAttachmentType
import com.togethertrip.main.post.domain.PostType
import java.math.BigDecimal
import java.time.Instant

/**
 * 게시글 작성 요청.
 * transactionId 가 있으면 거래 기반 기록, 없으면 일반 여행 기록으로 생성한다.
 */
data class CreatePostRequest(
    val transactionId: Long? = null,
    val title: String? = null,
    val category: String? = null,
    val content: String? = null,
    val postType: PostType = PostType.RECORD,
    val occurredAt: Instant? = null,
    val placeName: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val attachments: List<PostAttachmentRequest> = emptyList(),
)

data class PostAttachmentRequest(
    val attachmentType: PostAttachmentType,
    val fileUrl: String,
    val thumbnailUrl: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val sortOrder: Int = 0,
)
