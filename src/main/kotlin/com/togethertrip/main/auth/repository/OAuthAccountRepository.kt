package com.togethertrip.main.auth.repository

import com.togethertrip.main.auth.domain.OAuthAccount
import com.togethertrip.main.auth.domain.OAuthProvider
import org.springframework.data.jpa.repository.JpaRepository

interface OAuthAccountRepository : JpaRepository<OAuthAccount, Long> {

    fun findByProviderAndProviderUserId(
        provider: OAuthProvider,
        providerUserId: String,
    ): OAuthAccount?

    fun findAllByUserIdAndProvider(
        userId: Long,
        provider: OAuthProvider,
    ): List<OAuthAccount>

    fun deleteAllByUserId(userId: Long)
}
