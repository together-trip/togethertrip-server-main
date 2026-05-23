package com.togethertrip.main.post.dto.request

import com.togethertrip.main.post.domain.PostVisibility

data class UpdatePostRequest(
    val content: String? = null,
    val visibility: PostVisibility? = null,
)
