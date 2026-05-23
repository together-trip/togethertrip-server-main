package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.PostLike
import org.springframework.data.jpa.repository.JpaRepository

interface PostLikeRepository : JpaRepository<PostLike, Long> {

    fun countByPostIdAndDeletedAtIsNull(postId: Long): Long

    fun findByPostIdAndTripParticipantIdAndDeletedAtIsNull(
        postId: Long,
        tripParticipantId: Long,
    ): PostLike?
}
