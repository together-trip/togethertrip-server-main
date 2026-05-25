package com.togethertrip.main.post.dto.request

data class UpdatePostRequest(
    val title: String? = null,
    val category: String? = null,
    val content: String? = null,
)
