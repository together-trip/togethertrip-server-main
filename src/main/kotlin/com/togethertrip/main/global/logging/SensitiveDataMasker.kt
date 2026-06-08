package com.togethertrip.main.global.logging

object SensitiveDataMasker {
    private const val MASK = "***"
    private const val MAX_SUMMARY_LENGTH = 200

    private val keyValuePatterns = listOf(
        Regex("(?i)(password|passwd|pwd|token|accessToken|refreshToken|authorization|secret|apiKey|apiSecret|verificationCode|code)=([^,)}\\s]+)"),
        Regex("(?i)(\"(?:password|passwd|pwd|token|accessToken|refreshToken|authorization|secret|apiKey|apiSecret|verificationCode|code)\"\\s*:\\s*\")([^\"]+)(\")"),
    )
    private val bearerTokenPattern = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+")
    private val phonePattern = Regex("(?<!\\d)(?:\\+8210\\d{8}|010[- ]?\\d{4}[- ]?\\d{4})(?!\\d)")
    private val emailPattern = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val verificationCodePattern = Regex("(?i)(verificationCode|authCode|smsCode|code)([=:]\\s*)(\\d{4,8})")

    fun mask(value: String): String {
        var masked = value

        keyValuePatterns.forEach { pattern ->
            masked = pattern.replace(masked) { match ->
                if (match.groupValues.size == 4) {
                    "${match.groupValues[1]}$MASK${match.groupValues[3]}"
                } else {
                    "${match.groupValues[1]}=$MASK"
                }
            }
        }

        masked = bearerTokenPattern.replace(masked, "Bearer $MASK")
        masked = verificationCodePattern.replace(masked) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}$MASK"
        }
        masked = phonePattern.replace(masked, MASK)
        masked = emailPattern.replace(masked, MASK)

        return masked
    }

    fun summarize(value: Any?): String {
        val summary = when (value) {
            null -> "null"
            is CharSequence -> value.toString()
            is Number, is Boolean, is Enum<*> -> value.toString()
            is Collection<*> -> "${value::class.simpleName}(size=${value.size})"
            is Map<*, *> -> "${value::class.simpleName}(size=${value.size}, keys=${value.keys.joinToString(limit = 5)})"
            is Array<*> -> "Array(size=${value.size})"
            else -> value.toString()
        }

        return mask(summary).take(MAX_SUMMARY_LENGTH)
    }
}
