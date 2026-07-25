package com.togethertrip.main.post.repository

data class PostCommentCountRow(
    override val postId: Long,
    override val commentCount: Long,
) : PostCommentCountProjection
