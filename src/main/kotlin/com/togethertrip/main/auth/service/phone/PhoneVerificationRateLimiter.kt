package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.ErrorCode
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

    fun validate(phoneNumber: String) {
        if (redisTemplate.hasKey(getRateLimitKey(phoneNumber))) {
            throw BusinessException(ErrorCode.PHONE_VERIFICATION_REQUEST_TOO_SOON)
        }

        val dailyLimitKey = getDailyLimitKey(phoneNumber)
        val dailyCount = redisTemplate.opsForValue()
            .increment(dailyLimitKey) ?: 0

        if (dailyCount == 1L) {
            redisTemplate.expire(
                dailyLimitKey,
                DAILY_LIMIT_TTL,
            )
        }

        if (dailyCount > DAILY_REQUEST_LIMIT) {
            throw BusinessException(ErrorCode.PHONE_VERIFICATION_DAILY_LIMIT_EXCEEDED)
        }

        redisTemplate.opsForValue().set(
            getRateLimitKey(phoneNumber),
            "1",
            REQUEST_INTERVAL,
        )
    }

    private fun getRateLimitKey(phoneNumber: String): String {
        return "auth:phone-verification:rate:$phoneNumber"
    }

    private fun getDailyLimitKey(phoneNumber: String): String {
        val today = LocalDate.now(SEOUL_ZONE)
            .format(DateTimeFormatter.BASIC_ISO_DATE)

        return "auth:phone-verification:daily:$phoneNumber:$today"
    }

    companion object {
        private val REQUEST_INTERVAL: Duration = Duration.ofMinutes(1)
        private val DAILY_LIMIT_TTL: Duration = Duration.ofDays(2)
        private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private const val DAILY_REQUEST_LIMIT = 5
    }
}
