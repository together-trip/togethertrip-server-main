package com.togethertrip.main.global.logging

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SensitiveDataMaskerTest {

    @Test
    fun `Bearer 토큰을 마스킹한다`() {
        val masked = SensitiveDataMasker.mask("Authorization: Bearer abc.def.ghi")

        assertEquals("Authorization: Bearer ***", masked)
    }

    @Test
    fun `전화번호와 이메일을 마스킹한다`() {
        val masked = SensitiveDataMasker.mask("phone=010-1234-5678 email=user@example.com")

        assertFalse(masked.contains("010-1234-5678"))
        assertFalse(masked.contains("user@example.com"))
        assertContains(masked, "***")
    }

    @Test
    fun `인증번호와 토큰 키 값을 마스킹한다`() {
        val masked = SensitiveDataMasker.mask("verificationCode=123456, accessToken=abcdef")

        assertFalse(masked.contains("123456"))
        assertFalse(masked.contains("abcdef"))
        assertContains(masked, "verificationCode=***")
        assertContains(masked, "accessToken=***")
    }

    @Test
    fun `요약 문자열은 길이를 제한하고 민감정보를 마스킹한다`() {
        val summarized = SensitiveDataMasker.summarize(
            "token=secret-value ${"a".repeat(300)}",
        )

        assertFalse(summarized.contains("secret-value"))
        assertEquals(200, summarized.length)
    }
}
