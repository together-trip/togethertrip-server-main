package com.togethertrip.main.auth.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class KakaoLoginRequest(
    @field:NotBlank
    @field:Schema(
        description = "카카오 OAuth 액세스 토큰. local 프로필에서는 Swagger 테스트용으로 local-test:{id} 값을 사용할 수 있습니다.",
        example = "local-test:swagger",
    )
    val accessToken: String,
)
