package com.togethertrip.main.auth.service.phone

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
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
        val result = redisTemplate.execute(
            VALIDATE_SCRIPT,
            listOf(
                getRateLimitKey(phoneNumberHash),
                getDailyLimitKey(phoneNumberHash),
            ),
            REQUEST_INTERVAL.seconds.toString(),
            DAILY_LIMIT_TTL.seconds.toString(),
            DAILY_REQUEST_LIMIT.toString(),
        )

        when (result) {
            ALLOWED -> Unit
            RATE_LIMITED -> throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_REQUEST_TOO_SOON)
            DAILY_LIMITED -> throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_DAILY_LIMIT_EXCEEDED)
            else -> error("Unexpected phone verification rate limit result: $result")
        }
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
        private const val ALLOWED = 0L
        private const val RATE_LIMITED = 1L
        private const val DAILY_LIMITED = 2L
        private val VALIDATE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 1
            end

            local dailyCount = redis.call('INCR', KEYS[2])
            if dailyCount == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[2])
            end
            if dailyCount > tonumber(ARGV[3]) then
                return 2
            end

            redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
