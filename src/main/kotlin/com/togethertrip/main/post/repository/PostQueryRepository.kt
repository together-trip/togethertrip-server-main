package com.togethertrip.main.post.repository

import com.togethertrip.main.post.domain.Post
import org.springframework.data.domain.Pageable

fun interface PostQueryRepository {
    fun findPosts(
        condition: PostSearchCondition,
        pageable: Pageable,
    ): List<Post>
}
