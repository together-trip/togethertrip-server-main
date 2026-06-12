package com.togethertrip.main.global.phone

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PhoneNumberHasherTest {

    private val phoneNumberHasher = PhoneNumberHasher(
        key = "test-phone-hash-key-must-be-at-least-32-bytes",
        version = "v1",
    )

    @Test
    fun `같은 정규화 전화번호는 같은 해시를 반환한다`() {
        val firstHash = phoneNumberHasher.hash("+821012345678")
        val secondHash = phoneNumberHasher.hash("+821012345678")

        assertEquals(firstHash, secondHash)
    }

    @Test
    fun `다른 정규화 전화번호는 다른 해시를 반환한다`() {
        val firstHash = phoneNumberHasher.hash("+821012345678")
        val secondHash = phoneNumberHasher.hash("+821087654321")

        assertNotEquals(firstHash, secondHash)
    }

    @Test
    fun `해시 결과는 sha256 hex 길이를 가진다`() {
        val phoneNumberHash = phoneNumberHasher.hash("+821012345678")

        assertEquals(64, phoneNumberHash.length)
    }
}
