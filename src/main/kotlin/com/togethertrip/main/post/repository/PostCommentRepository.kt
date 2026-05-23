package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.PostComment
import org.springframework.data.jpa.repository.JpaRepository

interface PostCommentRepository : JpaRepository<PostComment, Long> {

    fun findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId: Long): List<PostComment>
}
