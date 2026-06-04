package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import org.springframework.data.redis.core.StringRedisTemplate
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

        return objectMapper.readValue(value, PhoneVerificationState::class.java)
    }

    fun delete(temporaryToken: String) {
        redisTemplate.delete(getVerificationKey(temporaryToken))
    }

    fun saveAttemptFailure(
        temporaryToken: String,
        state: PhoneVerificationState,
        maxAttemptCount: Int,
    ) {
        val nextState = state.copy(attemptCount = state.attemptCount + 1)

        if (nextState.attemptCount >= maxAttemptCount) {
            delete(temporaryToken)
            throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_ATTEMPT_EXCEEDED)
        }

        val remainingTtl = Duration
            .between(Instant.now(), nextState.expiresAt)
            .takeIf { !it.isNegative && !it.isZero }
            ?: Duration.ofSeconds(1)

        save(
            temporaryToken = temporaryToken,
            state = nextState,
            ttl = remainingTtl,
        )
    }

    private fun getVerificationKey(temporaryToken: String): String {
        return "auth:phone-verification:$temporaryToken"
    }
}
