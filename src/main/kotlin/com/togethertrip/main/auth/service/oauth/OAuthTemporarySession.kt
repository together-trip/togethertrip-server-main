package com.togethertrip.main.auth.service.oauth

import com.togethertrip.main.auth.domain.OAuthProvider

data class OAuthTemporarySession(
    val provider: OAuthProvider,
    val providerUserId: String,
    val nickname: String?,
    val profileImageUrl: String?,
    val existingUserId: Long?,
)
