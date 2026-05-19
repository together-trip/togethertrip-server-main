package com.togethertrip.main.auth.dto

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
)