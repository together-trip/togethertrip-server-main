package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.Post
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PostRepository : JpaRepository<Post, Long>, PostQueryRepository {

    fun findByIdAndTripIdAndDeletedAtIsNull(
        id: Long,
        tripId: Long,
    ): Post?

    fun findByTransactionIdAndDeletedAtIsNull(transactionId: Long): List<Post>

    fun existsByTransactionIdAndDeletedAtIsNull(transactionId: Long): Boolean

    @Query(
        """
        SELECT p
        FROM Post p
        WHERE p.trip.id = :tripId
          AND p.deletedAt IS NULL
        ORDER BY COALESCE(p.occurredAt, p.createdAt) ASC, p.id ASC
        """
    )
    fun findTripRecapSourcePosts(
        tripId: Long,
        pageable: Pageable,
    ): List<Post>
}
