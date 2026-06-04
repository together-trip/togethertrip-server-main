package com.togethertrip.main.auth.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class TokenRefreshRequest(
    @field:NotBlank
    @field:Schema(
        description = "로그인 또는 전화번호 인증 완료 응답으로 받은 refreshToken",
        example = "eyJhbGciOiJIUzI1NiJ9.refresh.token",
    )
    val refreshToken: String,
)
