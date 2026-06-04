package com.togethertrip.main.auth.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class RequestPhoneVerificationRequest(
    @field:NotBlank
    @field:Schema(
        description = "카카오 로그인 응답이 PHONE_VERIFICATION_REQUIRED일 때 내려주는 임시 토큰",
        example = "2f1d9f6d-5c3a-4f76-9a4e-c38d89336c33",
    )
    val temporaryToken: String,

    @field:NotBlank
    @field:Schema(
        description = "SMS 인증번호를 받을 실제 전화번호",
        example = "01012345678",
    )
    val phoneNumber: String,
)
