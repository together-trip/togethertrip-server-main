package com.togethertrip.main.user.dto.response

import io.swagger.v3.oas.annotations.media.Schema

data class NicknameAvailabilityResponse(
    @field:Schema(
        description = "닉네임 사용 가능 여부",
        example = "true",
    )
    val available: Boolean,
)
