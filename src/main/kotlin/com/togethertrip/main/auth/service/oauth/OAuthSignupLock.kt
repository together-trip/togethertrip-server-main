package com.togethertrip.main.auth.service.oauth

interface OAuthSignupLock {
    fun <T> withLock(
        session: OAuthTemporarySession,
        block: () -> T,
    ): T
}
