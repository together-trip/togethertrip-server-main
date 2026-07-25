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
          AND (p.postType = com.togethertrip.main.post.domain.PostType.EXPENSE OR (
            p.moderationHiddenAt IS NULL AND p.moderationDeletedAt IS NULL
            AND (:viewerUserId IS NULL OR NOT EXISTS (
              SELECT b.id FROM UserBlock b
              WHERE b.deletedAt IS NULL
                AND ((b.blocker.id = :viewerUserId AND b.blocked.id = p.author.user.id)
                  OR (b.blocker.id = p.author.user.id AND b.blocked.id = :viewerUserId))
            ))
          ))
        ORDER BY p.createdAt DESC, p.id DESC
        """
    )
    fun findPosts(
        tripId: Long,
        pageable: Pageable,
        viewerUserId: Long? = null,
    ): List<Post>

    @Query(
        """
        SELECT p
        FROM Post p
        WHERE p.trip.id = :tripId
          AND p.deletedAt IS NULL
          AND p.postType = :postType
          AND (p.postType = com.togethertrip.main.post.domain.PostType.EXPENSE OR (
            p.moderationHiddenAt IS NULL AND p.moderationDeletedAt IS NULL
            AND (:viewerUserId IS NULL OR NOT EXISTS (
              SELECT b.id FROM UserBlock b
              WHERE b.deletedAt IS NULL
                AND ((b.blocker.id = :viewerUserId AND b.blocked.id = p.author.user.id)
                  OR (b.blocker.id = p.author.user.id AND b.blocked.id = :viewerUserId))
            ))
          ))
        ORDER BY p.createdAt DESC, p.id DESC
        """
    )
    fun findPostsByType(
        tripId: Long,
        postType: PostType,
        pageable: Pageable,
        viewerUserId: Long? = null,
    ): List<Post>

    @Query(
        """
        SELECT p
        FROM Post p
        WHERE p.trip.id = :tripId
          AND p.deletedAt IS NULL
          AND (p.postType = com.togethertrip.main.post.domain.PostType.EXPENSE OR (
            p.moderationHiddenAt IS NULL AND p.moderationDeletedAt IS NULL
            AND (:viewerUserId IS NULL OR NOT EXISTS (
              SELECT b.id FROM UserBlock b
              WHERE b.deletedAt IS NULL
                AND ((b.blocker.id = :viewerUserId AND b.blocked.id = p.author.user.id)
                  OR (b.blocker.id = p.author.user.id AND b.blocked.id = :viewerUserId))
            ))
          ))
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
        viewerUserId: Long? = null,
    ): List<Post>

    @Query(
        """
        SELECT p
        FROM Post p
        WHERE p.trip.id = :tripId
          AND p.deletedAt IS NULL
          AND p.postType = :postType
          AND (p.postType = com.togethertrip.main.post.domain.PostType.EXPENSE OR (
            p.moderationHiddenAt IS NULL AND p.moderationDeletedAt IS NULL
            AND (:viewerUserId IS NULL OR NOT EXISTS (
              SELECT b.id FROM UserBlock b
              WHERE b.deletedAt IS NULL
                AND ((b.blocker.id = :viewerUserId AND b.blocked.id = p.author.user.id)
                  OR (b.blocker.id = p.author.user.id AND b.blocked.id = :viewerUserId))
            ))
          ))
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
        viewerUserId: Long? = null,
    ): List<Post>

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
