package com.togethertrip.main.auth.dto

data class TokenRefreshRequest(
    val refreshToken: String,
)