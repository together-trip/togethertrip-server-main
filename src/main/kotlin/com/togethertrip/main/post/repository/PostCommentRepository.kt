package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.PostComment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface PostCommentRepository : JpaRepository<PostComment, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): PostComment?

    @Query(
        """
        SELECT COUNT(c)
        FROM PostComment c
        WHERE c.post.id = :postId
          AND c.parentComment IS NULL
          AND c.deletedAt IS NULL
          AND c.moderationHiddenAt IS NULL
          AND c.moderationDeletedAt IS NULL
          AND (:viewerUserId IS NULL OR NOT EXISTS (
            SELECT b.id FROM UserBlock b
            WHERE b.deletedAt IS NULL
              AND ((b.blocker.id = :viewerUserId AND b.blocked.id = c.author.user.id)
                OR (b.blocker.id = c.author.user.id AND b.blocked.id = :viewerUserId))
          ))
        """
    )
    fun countVisibleRootComments(postId: Long, viewerUserId: Long?): Long

    @Query(
        """
        SELECT c.post.id AS postId, COUNT(c) AS commentCount
        FROM PostComment c
        WHERE c.post.id IN :postIds
          AND c.parentComment IS NULL
          AND c.deletedAt IS NULL
          AND c.moderationHiddenAt IS NULL
          AND c.moderationDeletedAt IS NULL
          AND (:viewerUserId IS NULL OR NOT EXISTS (
            SELECT b.id FROM UserBlock b
            WHERE b.deletedAt IS NULL
              AND ((b.blocker.id = :viewerUserId AND b.blocked.id = c.author.user.id)
                OR (b.blocker.id = c.author.user.id AND b.blocked.id = :viewerUserId))
          ))
        GROUP BY c.post.id
        """
    )
    fun findVisibleCommentCounts(postIds: Collection<Long>, viewerUserId: Long?): List<PostCommentCountProjection>

    @Query(
        """
        SELECT c
        FROM PostComment c
        WHERE c.post.id = :postId
          AND c.parentComment IS NULL
          AND c.deletedAt IS NULL
          AND c.moderationHiddenAt IS NULL
          AND c.moderationDeletedAt IS NULL
          AND (:viewerUserId IS NULL OR NOT EXISTS (
            SELECT b.id FROM UserBlock b
            WHERE b.deletedAt IS NULL
              AND ((b.blocker.id = :viewerUserId AND b.blocked.id = c.author.user.id)
                OR (b.blocker.id = c.author.user.id AND b.blocked.id = :viewerUserId))
          ))
        ORDER BY c.createdAt ASC, c.id ASC
        """
    )
    fun findRootComments(
        postId: Long,
        pageable: Pageable,
        viewerUserId: Long? = null,
    ): List<PostComment>

    @Query(
        """
        SELECT c
        FROM PostComment c
        WHERE c.post.id = :postId
          AND c.parentComment IS NULL
          AND c.deletedAt IS NULL
          AND c.moderationHiddenAt IS NULL
          AND c.moderationDeletedAt IS NULL
          AND (:viewerUserId IS NULL OR NOT EXISTS (
            SELECT b.id FROM UserBlock b
            WHERE b.deletedAt IS NULL
              AND ((b.blocker.id = :viewerUserId AND b.blocked.id = c.author.user.id)
                OR (b.blocker.id = c.author.user.id AND b.blocked.id = :viewerUserId))
          ))
          AND (
            c.createdAt > :cursorCreatedAt
            OR (c.createdAt = :cursorCreatedAt AND c.id > :cursorId)
          )
        ORDER BY c.createdAt ASC, c.id ASC
        """
    )
    fun findRootCommentsByCursor(
        postId: Long,
        cursorCreatedAt: Instant,
        cursorId: Long,
        pageable: Pageable,
        viewerUserId: Long? = null,
    ): List<PostComment>

    fun findByIdAndPostIdAndParentCommentIsNullAndDeletedAtIsNull(
        id: Long,
        postId: Long,
    ): PostComment?
}
