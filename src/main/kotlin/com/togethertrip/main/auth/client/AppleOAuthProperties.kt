package com.togethertrip.main.auth.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth.apple")
data class AppleOAuthProperties(
    val enabled: Boolean = false,
    val clientId: String = "",
    val teamId: String = "",
    val keyId: String = "",
    val privateKey: String = "",
    val tokenEncryptionKey: String = "",
    val issuer: String = "https://appleid.apple.com",
    val jwksUrl: String = "https://appleid.apple.com/auth/keys",
    val tokenUrl: String = "https://appleid.apple.com/auth/token",
    val revokeUrl: String = "https://appleid.apple.com/auth/revoke",
)
