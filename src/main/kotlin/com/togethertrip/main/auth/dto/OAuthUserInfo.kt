package com.togethertrip.main.auth.dto

import com.togethertrip.main.auth.domain.OAuthProvider

data class OAuthUserInfo(
    val provider: OAuthProvider,
    val providerUserId: String,
    val email: String?,
    val nickname: String?,
    val profileImageUrl: String?,
)