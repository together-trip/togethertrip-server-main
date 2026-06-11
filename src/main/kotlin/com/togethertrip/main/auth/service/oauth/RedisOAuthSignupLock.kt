package com.togethertrip.main.auth.service.oauth

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
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
        // 가입 잠금 key와 token 생성
        val key = getKey(session)
        val lockToken = UUID.randomUUID().toString()

        // 가입 잠금 획득
        val acquired = redisTemplate
            .opsForValue()
            .setIfAbsent(key, lockToken, LOCK_TTL) == true

        // 가입 진행 중 여부 확인
        if (!acquired) {
            throw BusinessException(AuthErrorCode.SIGNUP_CONFIRMATION_IN_PROGRESS)
        }

        // 트랜잭션 종료 후 잠금 해제 등록
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

        // 가입 처리 실행
        try {
            return block()
        } finally {
            // 트랜잭션이 없으면 즉시 잠금 해제
            if (!releaseAfterTransaction) {
                release(key, lockToken)
            }
        }
    }

    private fun release(
        key: String,
        lockToken: String,
    ) {
        // token 확인과 삭제를 원자적으로 실행
        redisTemplate.execute(
            RELEASE_SCRIPT,
            listOf(key),
            lockToken,
        )
    }

    private fun getKey(session: OAuthTemporarySession): String {
        // OAuth 가입 잠금 key
        return "auth:oauth-signup-lock:${session.provider}:${session.providerUserId}"
    }

    companion object {
        private val LOCK_TTL: Duration = Duration.ofSeconds(10)
        private val RELEASE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
