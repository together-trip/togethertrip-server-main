package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.PostType
import java.time.Instant

data class PostSearchCondition(
    val tripId: Long,
    val postType: PostType?,
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
