package com.togethertrip.main.auth.dto.response

import com.togethertrip.main.auth.dto.TokenResponse

data class AuthResponse(
    val status: AuthStatus,
    val temporaryToken: String?,
    val accessToken: String?,
    val refreshToken: String?,
) {

    companion object {
        fun authenticated(tokenResponse: TokenResponse): AuthResponse {
            return AuthResponse(
                status = AuthStatus.AUTHENTICATED,
                temporaryToken = null,
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
            )
        }

        fun phoneVerificationRequired(temporaryToken: String): AuthResponse {
            return AuthResponse(
                status = AuthStatus.PHONE_VERIFICATION_REQUIRED,
                temporaryToken = temporaryToken,
                accessToken = null,
                refreshToken = null,
            )
        }
    }
}
