package com.togethertrip.main.auth.service.oauth

import com.togethertrip.main.auth.domain.OAuthProvider

interface OAuthSignupLock {
    fun <T> withLock(
        provider: OAuthProvider,
        providerUserId: String,
        block: () -> T,
    ): T
}
