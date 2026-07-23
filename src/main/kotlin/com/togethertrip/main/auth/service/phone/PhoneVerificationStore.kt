package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant

@Component
class PhoneVerificationStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun save(
        temporaryToken: String,
        state: PhoneVerificationState,
        ttl: Duration,
    ) {
        redisTemplate.opsForValue().set(
            getVerificationKey(temporaryToken),
            objectMapper.writeValueAsString(state),
            ttl,
        )
    }

    fun get(temporaryToken: String): PhoneVerificationState {
        val value = redisTemplate.opsForValue().get(getVerificationKey(temporaryToken))
            ?: throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_CODE_EXPIRED)

        // Redis 인증 상태 역직렬화
        return objectMapper.readValue(value, PhoneVerificationState::class.java)
    }

    fun delete(temporaryToken: String) {
        redisTemplate.delete(
            listOf(
                getVerificationKey(temporaryToken),
                getAttemptKey(temporaryToken),
            )
        )
    }

    fun incrementAttemptFailure(
        temporaryToken: String,
        state: PhoneVerificationState,
        maxAttemptCount: Int,
    ) {
        val remainingTtl = Duration
            .between(Instant.now(), state.expiresAt)
            .takeIf { !it.isNegative && !it.isZero }
            ?: Duration.ofSeconds(1)

        val result = redisTemplate.execute(
            INCREMENT_ATTEMPT_SCRIPT,
            listOf(
                getVerificationKey(temporaryToken),
                getAttemptKey(temporaryToken),
            ),
            remainingTtl.toMillis().coerceAtLeast(1).toString(),
            maxAttemptCount.toString(),
        )

        when (result) {
            ATTEMPT_RECORDED -> Unit
            ATTEMPT_EXCEEDED -> throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_ATTEMPT_EXCEEDED)
            VERIFICATION_EXPIRED -> throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_CODE_EXPIRED)
            else -> error("Unexpected phone verification attempt result: $result")
        }
    }

    private fun getVerificationKey(temporaryToken: String): String {
        return "auth:phone-verification:$temporaryToken"
    }

    private fun getAttemptKey(temporaryToken: String): String {
        return "auth:phone-verification:attempt:$temporaryToken"
    }

    companion object {
        private const val ATTEMPT_RECORDED = 0L
        private const val ATTEMPT_EXCEEDED = 1L
        private const val VERIFICATION_EXPIRED = 2L
        private val INCREMENT_ATTEMPT_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 2
            end

            local attemptCount = redis.call('INCR', KEYS[2])
            if attemptCount == 1 then
                redis.call('PEXPIRE', KEYS[2], ARGV[1])
            end
            if attemptCount >= tonumber(ARGV[2]) then
                redis.call('DEL', KEYS[1], KEYS[2])
                return 1
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
