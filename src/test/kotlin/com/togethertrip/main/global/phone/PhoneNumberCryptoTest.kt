package com.togethertrip.main.global.phone

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PhoneNumberCryptoTest {

    private val phoneNumberCrypto = PhoneNumberCrypto(
        key = "test-phone-encryption-key-must-be-at-least-32-bytes",
        version = "v1",
    )

    @Test
    fun `전화번호를 암호화하고 복호화한다`() {
        val encryptedPhoneNumber = phoneNumberCrypto.encrypt("+821012345678")

        assertEquals("+821012345678", phoneNumberCrypto.decrypt(encryptedPhoneNumber))
    }

    @Test
    fun `같은 전화번호를 두 번 암호화해도 암호문은 다르다`() {
        val firstEncryptedPhoneNumber = phoneNumberCrypto.encrypt("+821012345678")
        val secondEncryptedPhoneNumber = phoneNumberCrypto.encrypt("+821012345678")

        assertNotEquals(firstEncryptedPhoneNumber, secondEncryptedPhoneNumber)
    }

    @Test
    fun `정규화 전화번호를 마스킹한다`() {
        assertEquals("010-****-5678", phoneNumberCrypto.mask("+821012345678"))
    }
}
