package com.togethertrip.main.post.dto.response

import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostAttachment
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.trip.dto.response.TripParticipantDisplay
import java.math.BigDecimal
import java.time.Instant

data class PostDetailResponse(
    val id: Long,
    val tripId: Long,
    val transactionId: Long?,
    val authorParticipantId: Long,
    val authorUserId: Long?,
    val authorDisplayName: String,
    val postType: PostType,
    val title: String?,
    val category: String?,
    val content: String?,
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
        fun from(
            post: Post,
            attachments: List<PostAttachment>,
            commentCount: Int = post.commentCount,
        ): PostDetailResponse {
            return PostDetailResponse(
                id = post.id,
                tripId = post.trip.id,
                transactionId = post.transaction?.id,
                authorParticipantId = post.author.id,
                authorUserId = post.author.user?.id,
                authorDisplayName = TripParticipantDisplay.displayName(post.author),
                postType = post.postType,
                title = post.title,
                category = post.category,
                content = post.content,
                occurredAt = post.occurredAt,
                placeName = post.placeName,
                latitude = post.latitude,
                longitude = post.longitude,
                commentCount = commentCount,
                attachments = attachments.map(PostAttachmentResponse::from),
                createdAt = post.createdAt,
                updatedAt = post.updatedAt,
            )
        }
    }
}
