package com.togethertrip.main.global.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String,
    val accessTokenExpiration: Long,
    val refreshTokenExpiration: Long,
)
