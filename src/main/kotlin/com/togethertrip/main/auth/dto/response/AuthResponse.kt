package com.togethertrip.main.auth.dto.response

import com.togethertrip.main.auth.dto.TokenResponse

data class AuthResponse(
    val status: AuthStatus,
    val accessToken: String?,
    val refreshToken: String?,
) {

    companion object {
        fun authenticated(tokenResponse: TokenResponse): AuthResponse {
            return AuthResponse(
                status = AuthStatus.AUTHENTICATED,
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
            )
        }

        fun profileRequired(tokenResponse: TokenResponse): AuthResponse {
            return AuthResponse(
                status = AuthStatus.PROFILE_REQUIRED,
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
            )
        }

    }
}
