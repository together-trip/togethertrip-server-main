package com.togethertrip.main.user.dto.request

import jakarta.validation.constraints.Size

data class UpdateUserRequest(
    @field:Size(max = 50)
    val nickname: String? = null,

    @field:Size(max = 500)
    val profileImageUrl: String? = null,
)
