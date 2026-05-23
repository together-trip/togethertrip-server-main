package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.Post
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface PostRepository : JpaRepository<Post, Long> {

    fun findByTripIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        tripId: Long,
        pageable: Pageable,
    ): Page<Post>

    fun findByTransactionIdAndDeletedAtIsNull(transactionId: Long): List<Post>
}
