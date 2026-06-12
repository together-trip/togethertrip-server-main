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
        // 인증 실패 횟수 증가
        val attemptCount = redisTemplate.opsForValue()
            .increment(getAttemptKey(temporaryToken)) ?: 1L

        // attempt key TTL 계산
        val remainingTtl = Duration
            .between(Instant.now(), state.expiresAt)
            .takeIf { !it.isNegative && !it.isZero }
            ?: Duration.ofSeconds(1)

        // 최초 실패 TTL 설정
        if (attemptCount == 1L) {
            redisTemplate.expire(
                getAttemptKey(temporaryToken),
                remainingTtl,
            )
        }

        // 최대 실패 횟수 확인
        if (attemptCount >= maxAttemptCount) {
            delete(temporaryToken)
            throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_ATTEMPT_EXCEEDED)
        }
    }

    private fun getVerificationKey(temporaryToken: String): String {
        return "auth:phone-verification:$temporaryToken"
    }

    private fun getAttemptKey(temporaryToken: String): String {
        return "auth:phone-verification:attempt:$temporaryToken"
    }
}
