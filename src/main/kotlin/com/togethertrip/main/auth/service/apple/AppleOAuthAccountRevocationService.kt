package com.togethertrip.main.auth.service.apple

import com.togethertrip.main.auth.client.AppleOAuthClient
import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.repository.OAuthAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class AppleOAuthAccountRevocationService(
    private val oauthAccountRepository: OAuthAccountRepository,
    private val appleOAuthClient: AppleOAuthClient,
    private val appleTokenCipher: AppleTokenCipher,
) : OAuthAccountRevoker {
    override fun revokeForUser(userId: Long) {
        val encryptedTokens = oauthAccountRepository
            .findAllByUserIdAndProvider(userId, OAuthProvider.APPLE)
            .mapNotNull { it.encryptedRefreshToken }
        val revoke = {
            encryptedTokens.forEach { encryptedToken ->
                try {
                    appleOAuthClient.revoke(appleTokenCipher.decrypt(encryptedToken))
                } catch (exception: Exception) {
                    logger.warn("Apple token revocation failed for userId={}", userId, exception)
                }
            }
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = revoke()
                }
            )
        } else {
            revoke()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AppleOAuthAccountRevocationService::class.java)
    }
}
