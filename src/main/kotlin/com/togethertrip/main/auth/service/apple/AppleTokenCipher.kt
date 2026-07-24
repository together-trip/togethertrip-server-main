package com.togethertrip.main.auth.service.apple

import com.togethertrip.main.auth.client.AppleOAuthProperties
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AppleTokenCipher(
    properties: AppleOAuthProperties,
) {
    private val key = properties.tokenEncryptionKey
        .takeIf { it.isNotBlank() }
        ?.let { Base64.getDecoder().decode(it) }
        ?.also { require(it.size == 32) { "APPLE_TOKEN_ENCRYPTION_KEY must be a Base64-encoded 32-byte key" } }

    fun encrypt(value: String): String {
        val secretKey = requireNotNull(key) { "APPLE_TOKEN_ENCRYPTION_KEY is required for Apple login" }
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secretKey, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(value: String): String {
        val secretKey = requireNotNull(key) { "APPLE_TOKEN_ENCRYPTION_KEY is required for Apple token revocation" }
        val decoded = Base64.getDecoder().decode(value)
        require(decoded.size > 28) { "Invalid encrypted Apple token" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(secretKey, "AES"),
            GCMParameterSpec(128, decoded.copyOfRange(0, 12)),
        )
        return cipher.doFinal(decoded.copyOfRange(12, decoded.size)).toString(Charsets.UTF_8)
    }
}
