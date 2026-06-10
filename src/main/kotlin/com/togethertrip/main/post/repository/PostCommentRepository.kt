package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.PostComment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface PostCommentRepository : JpaRepository<PostComment, Long> {

    @Query(
        """
        SELECT c
        FROM PostComment c
        WHERE c.post.id = :postId
          AND c.parentComment IS NULL
          AND c.deletedAt IS NULL
        ORDER BY c.createdAt ASC, c.id ASC
        """
    )
    fun findRootComments(
        postId: Long,
        pageable: Pageable,
    ): List<PostComment>

    @Query(
        """
        SELECT c
        FROM PostComment c
        WHERE c.post.id = :postId
          AND c.parentComment IS NULL
          AND c.deletedAt IS NULL
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
    ): List<PostComment>

    fun findByIdAndPostIdAndParentCommentIsNullAndDeletedAtIsNull(
        id: Long,
        postId: Long,
    ): PostComment?
}
