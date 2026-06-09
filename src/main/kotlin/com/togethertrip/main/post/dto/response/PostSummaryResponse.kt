package com.togethertrip.main.post.dto.response

import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostAttachment
import com.togethertrip.main.post.domain.PostType
import java.math.BigDecimal
import java.time.Instant

data class PostSummaryResponse(
    val id: Long,
    val tripId: Long,
    val transactionId: Long?,
    val authorParticipantId: Long,
    val authorDisplayName: String,
    val postType: PostType,
    val title: String?,
    val category: String?,
    val contentPreview: String?,
    val occurredAt: Instant?,
    val placeName: String?,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val commentCount: Int,
    val attachments: List<PostAttachmentResponse>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        private const val PREVIEW_LENGTH = 120

        fun from(
            post: Post,
            attachments: List<PostAttachment> = emptyList(),
        ): PostSummaryResponse {
            return PostSummaryResponse(
                id = post.id,
                tripId = post.trip.id,
                transactionId = post.transaction?.id,
                authorParticipantId = post.author.id,
                authorDisplayName = post.author.displayName,
                postType = post.postType,
                title = post.title,
                category = post.category,
                contentPreview = post.content?.take(PREVIEW_LENGTH),
                occurredAt = post.occurredAt,
                placeName = post.placeName,
                latitude = post.latitude,
                longitude = post.longitude,
                commentCount = post.commentCount,
                attachments = attachments.map(PostAttachmentResponse::from),
                createdAt = post.createdAt,
                updatedAt = post.updatedAt,
            )
        }
    }
}
