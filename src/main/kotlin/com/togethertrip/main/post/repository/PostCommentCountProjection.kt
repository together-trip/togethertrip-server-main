package com.togethertrip.main.post.repository

interface PostCommentCountProjection {
    val postId: Long
    val commentCount: Long
}
