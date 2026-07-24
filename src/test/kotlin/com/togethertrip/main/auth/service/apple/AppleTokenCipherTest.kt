package com.togethertrip.main.auth.service.apple

import com.togethertrip.main.auth.client.AppleOAuthProperties
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith

class AppleTokenCipherTest {
    @Test
    fun `Apple refresh token을 AES GCM으로 암호화하고 복호화한다`() {
        val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val cipher = AppleTokenCipher(AppleOAuthProperties(tokenEncryptionKey = key))

        val encrypted = cipher.encrypt("apple-refresh-token")

        assertNotEquals("apple-refresh-token", encrypted)
        assertEquals("apple-refresh-token", cipher.decrypt(encrypted))
    }

    @Test
    fun `암호화 키가 없거나 ciphertext가 손상되면 실패한다`() {
        val withoutKey = AppleTokenCipher(AppleOAuthProperties())
        assertFailsWith<IllegalArgumentException> { withoutKey.encrypt("token") }
        assertFailsWith<IllegalArgumentException> { withoutKey.decrypt("token") }

        val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val cipher = AppleTokenCipher(AppleOAuthProperties(tokenEncryptionKey = key))
        val tooShort = Base64.getEncoder().encodeToString(ByteArray(12))
        assertFailsWith<IllegalArgumentException> { cipher.decrypt(tooShort) }
    }
}
