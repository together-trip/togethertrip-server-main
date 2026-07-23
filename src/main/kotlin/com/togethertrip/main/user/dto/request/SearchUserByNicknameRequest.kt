package com.togethertrip.main.user.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SearchUserByNicknameRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 20)
    val nickname: String,
)
