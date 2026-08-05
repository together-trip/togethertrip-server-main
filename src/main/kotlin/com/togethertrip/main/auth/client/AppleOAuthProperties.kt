package com.togethertrip.main.auth.client

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

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
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val responseTimeout: Duration = Duration.ofSeconds(5),
) {
    init {
        require(connectTimeout.toMillis() in MIN_TIMEOUT_MILLIS..MAX_TIMEOUT.toMillis()) {
            "Apple OAuth connect timeout must be between 1ms and 30s"
        }
        require(responseTimeout.toMillis() in MIN_TIMEOUT_MILLIS..MAX_TIMEOUT.toMillis()) {
            "Apple OAuth response timeout must be between 1ms and 30s"
        }
    }

    private companion object {
        const val MIN_TIMEOUT_MILLIS = 1L
        val MAX_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
