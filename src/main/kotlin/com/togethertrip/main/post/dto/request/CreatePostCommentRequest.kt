package com.togethertrip.main.post.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreatePostCommentRequest(
    @field:NotBlank
    @field:Size(max = 2000)
    val content: String,
)
