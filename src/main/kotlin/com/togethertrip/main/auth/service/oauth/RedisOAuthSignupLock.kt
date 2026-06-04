package com.togethertrip.main.auth.service.oauth

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.util.UUID

@Component
class RedisOAuthSignupLock(
    private val redisTemplate: StringRedisTemplate,
) : OAuthSignupLock {

    override fun <T> withLock(
        session: OAuthTemporarySession,
        block: () -> T,
    ): T {
        val key = getKey(session)
        val lockToken = UUID.randomUUID().toString()
        val acquired = redisTemplate
            .opsForValue()
            .setIfAbsent(key, lockToken, LOCK_TTL) == true

        if (!acquired) {
            throw BusinessException(AuthErrorCode.SIGNUP_CONFIRMATION_IN_PROGRESS)
        }

        val releaseAfterTransaction = TransactionSynchronizationManager.isSynchronizationActive()
        if (releaseAfterTransaction) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCompletion(status: Int) {
                        release(key, lockToken)
                    }
                }
            )
        }

        try {
            return block()
        } finally {
            if (!releaseAfterTransaction) {
                release(key, lockToken)
            }
        }
    }

    private fun release(
        key: String,
        lockToken: String,
    ) {
        if (redisTemplate.opsForValue().get(key) == lockToken) {
            redisTemplate.delete(key)
        }
    }

    private fun getKey(session: OAuthTemporarySession): String {
        return "auth:oauth-signup-lock:${session.provider}:${session.providerUserId}"
    }

    companion object {
        private val LOCK_TTL: Duration = Duration.ofSeconds(10)
    }
}
