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

    @Test
    fun `JSON 민감 key의 값은 원래 quote와 key를 유지하며 모두 마스킹한다`() {
        val masked = SensitiveDataMasker.mask(
            """{"password":"pw","refreshToken":"refresh","apiSecret":"secret","code":"123456"}"""
        )

        assertEquals(
            """{"password":"***","refreshToken":"***","apiSecret":"***","code":"***"}""",
            masked,
        )
    }

    @Test
    fun `대소문자가 다른 alias key와 복합 Bearer token도 마스킹한다`() {
        val masked = SensitiveDataMasker.mask(
            "PWD=pass, ApiKey=key, AUTHORIZATION=Bearer abc.DEF_123-+/="
        )

        assertFalse(masked.contains("pass"))
        assertFalse(masked.contains("abc.DEF_123-+/="))
        assertContains(masked, "PWD=***")
        assertContains(masked, "ApiKey=***")
    }

    @Test
    fun `E164와 공백 구분 국내 전화번호를 주변 숫자와 오탐 없이 마스킹한다`() {
        val masked = SensitiveDataMasker.mask(
            "e164=+821012345678 local=010 1234 5678 embedded=1010123456789"
        )

        assertFalse(masked.contains("+821012345678"))
        assertFalse(masked.contains("010 1234 5678"))
        assertContains(masked, "embedded=1010123456789")
    }

    @Test
    fun `요약은 null scalar collection map array와 object를 값 노출 없이 구분한다`() {
        assertEquals("null", SensitiveDataMasker.summarize(null))
        assertEquals("42", SensitiveDataMasker.summarize(42))
        assertEquals("true", SensitiveDataMasker.summarize(true))
        assertEquals("ACTIVE", SensitiveDataMasker.summarize(TestStatus.ACTIVE))
        assertEquals("ArrayList(size=2)", SensitiveDataMasker.summarize(arrayListOf("secret-a", "secret-b")))
        assertEquals("LinkedHashMap(size=2, keys=first, second)", SensitiveDataMasker.summarize(linkedMapOf("first" to "secret-a", "second" to "secret-b")))
        assertEquals("Array(size=2)", SensitiveDataMasker.summarize(arrayOf("secret-a", "secret-b")))
        assertEquals("safe-object", SensitiveDataMasker.summarize(object {
            override fun toString(): String = "safe-object"
        }))
    }

    private enum class TestStatus {
        ACTIVE,
    }
}
