package com.togethertrip.main.auth.dto.response

data class PhoneVerificationCodeSentResponse(
    val phoneNumber: String,
    val expiresInSeconds: Long,
)
