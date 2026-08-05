package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.PostComment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface PostCommentRepository : JpaRepository<PostComment, Long>, PostCommentQueryRepository {

    fun findByIdAndDeletedAtIsNull(id: Long): PostComment?

    fun findByIdAndPostIdAndParentCommentIsNullAndDeletedAtIsNull(
        id: Long,
        postId: Long,
    ): PostComment?
}
