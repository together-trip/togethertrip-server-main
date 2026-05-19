package com.togethertrip.main.global.security.jwt

import com.togethertrip.main.user.domain.UserRole

data class JwtClaims(
    val userId: Long,
    val role: UserRole,
    val tokenType: TokenType
)
