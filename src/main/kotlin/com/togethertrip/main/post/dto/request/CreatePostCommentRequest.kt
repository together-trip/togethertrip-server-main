package com.togethertrip.main.post.dto.request

import jakarta.validation.constraints.NotBlank

data class CreatePostCommentRequest(
    @field:NotBlank
    val content: String,
)
