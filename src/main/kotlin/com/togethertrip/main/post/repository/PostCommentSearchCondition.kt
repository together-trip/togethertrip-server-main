package com.togethertrip.main.post.repository

import java.time.Instant

data class PostCommentSearchCondition(
    val postId: Long,
    val viewerUserId: Long?,
    val cursorCreatedAt: Instant?,
    val cursorId: Long?,
) {
    init {
        require((cursorCreatedAt == null) == (cursorId == null)) {
            "cursorCreatedAt and cursorId must be provided together"
        }
    }
}
