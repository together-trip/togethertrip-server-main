package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class PhoneVerificationRateLimiter(
    private val redisTemplate: StringRedisTemplate,
) {

    fun validate(phoneNumberHash: String) {
        // 재요청 간격 확인
        if (redisTemplate.hasKey(getRateLimitKey(phoneNumberHash))) {
            throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_REQUEST_TOO_SOON)
        }

        // 일일 요청 횟수 증가
        val dailyLimitKey = getDailyLimitKey(phoneNumberHash)
        val dailyCount = redisTemplate.opsForValue()
            .increment(dailyLimitKey) ?: 0

        // 일일 요청 횟수 TTL 설정
        if (dailyCount == 1L) {
            redisTemplate.expire(
                dailyLimitKey,
                DAILY_LIMIT_TTL,
            )
        }

        // 일일 요청 한도 확인
        if (dailyCount > DAILY_REQUEST_LIMIT) {
            throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_DAILY_LIMIT_EXCEEDED)
        }

        // 재요청 간격 key 저장
        redisTemplate.opsForValue().set(
            getRateLimitKey(phoneNumberHash),
            "1",
            REQUEST_INTERVAL,
        )
    }

    private fun getRateLimitKey(phoneNumberHash: String): String {
        // 인증번호 재요청 제한 key
        return "auth:phone-verification:rate:$phoneNumberHash"
    }

    private fun getDailyLimitKey(phoneNumberHash: String): String {
        // 일일 요청 기준 날짜
        val today = LocalDate.now(SEOUL_ZONE)
            .format(DateTimeFormatter.BASIC_ISO_DATE)

        // 인증번호 일일 요청 제한 key
        return "auth:phone-verification:daily:$phoneNumberHash:$today"
    }

    companion object {
        private val REQUEST_INTERVAL: Duration = Duration.ofMinutes(1)
        private val DAILY_LIMIT_TTL: Duration = Duration.ofDays(2)
        private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private const val DAILY_REQUEST_LIMIT = 5
    }
}
