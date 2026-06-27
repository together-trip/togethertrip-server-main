package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface PostRepository : JpaRepository<Post, Long> {

    @Query(
        """
        SELECT p
        FROM Post p
        WHERE p.trip.id = :tripId
          AND p.deletedAt IS NULL
        ORDER BY p.createdAt DESC, p.id DESC
        """
    )
    fun findPosts(
        tripId: Long,
        pageable: Pageable,
    ): List<Post>

    @Query(
        """
        SELECT p
        FROM Post p
        WHERE p.trip.id = :tripId
          AND p.deletedAt IS NULL
          AND p.postType = :postType
        ORDER BY p.createdAt DESC, p.id DESC
        """
    )
    fun findPostsByType(
        tripId: Long,
        postType: PostType,
        pageable: Pageable,
    ): List<Post>

    @Query(
        """
        SELECT p
        FROM Post p
        WHERE p.trip.id = :tripId
          AND p.deletedAt IS NULL
          AND (
            p.createdAt < :cursorCreatedAt
            OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId)
          )
        ORDER BY p.createdAt DESC, p.id DESC
        """
    )
    fun findPostsByCursor(
        tripId: Long,
        cursorCreatedAt: Instant,
        cursorId: Long,
        pageable: Pageable,
    ): List<Post>

    @Query(
        """
        SELECT p
        FROM Post p
        WHERE p.trip.id = :tripId
          AND p.deletedAt IS NULL
          AND p.postType = :postType
          AND (
            p.createdAt < :cursorCreatedAt
            OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId)
          )
        ORDER BY p.createdAt DESC, p.id DESC
        """
    )
    fun findPostsByTypeAndCursor(
        tripId: Long,
        postType: PostType,
        cursorCreatedAt: Instant,
        cursorId: Long,
        pageable: Pageable,
    ): List<Post>

    fun findByIdAndTripIdAndDeletedAtIsNull(
        id: Long,
        tripId: Long,
    ): Post?

    fun findByTransactionIdAndDeletedAtIsNull(transactionId: Long): List<Post>

    fun existsByTransactionIdAndDeletedAtIsNull(transactionId: Long): Boolean
}
