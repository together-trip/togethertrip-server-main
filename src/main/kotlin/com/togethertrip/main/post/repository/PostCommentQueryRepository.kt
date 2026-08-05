package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.PostComment
import org.springframework.data.domain.Pageable

interface PostCommentQueryRepository {
    fun countVisibleRootComments(postId: Long, viewerUserId: Long?): Long

    fun findVisibleCommentCounts(
        postIds: Collection<Long>,
        viewerUserId: Long?,
    ): List<PostCommentCountProjection>

    fun findRootComments(
        condition: PostCommentSearchCondition,
        pageable: Pageable,
    ): List<PostComment>
}
