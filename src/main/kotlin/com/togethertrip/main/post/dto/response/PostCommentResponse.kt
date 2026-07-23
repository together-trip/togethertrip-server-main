package com.togethertrip.main.post.dto.response

import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.trip.dto.response.TripParticipantDisplay
import java.time.Instant

data class PostCommentResponse(
    val id: Long,
    val postId: Long,
    val authorParticipantId: Long,
    val authorDisplayName: String,
    val content: String,
    val commentDepth: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(comment: PostComment): PostCommentResponse {
            return PostCommentResponse(
                id = comment.id,
                postId = comment.post.id,
                authorParticipantId = comment.author.id,
                authorDisplayName = TripParticipantDisplay.displayName(comment.author),
                content = comment.content,
                commentDepth = comment.commentDepth,
                createdAt = comment.createdAt,
                updatedAt = comment.updatedAt,
            )
        }
    }
}
