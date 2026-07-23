package com.togethertrip.main.global.phone

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class PhoneNumberCrypto(
    @Value("\${phone.encryption.key}")
    private val key: String,
    @Value("\${phone.encryption.version}")
    val version: String,
) {

    init {
        require(key.length >= MIN_KEY_LENGTH) {
            "phone.encryption.key must be at least $MIN_KEY_LENGTH characters."
        }
        require(version.isNotBlank()) {
            "phone.encryption.version must not be blank."
        }
    }

    fun encrypt(normalizedPhoneNumber: String): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        SECURE_RANDOM.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(createAesKey(), AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )

        val encrypted = cipher.doFinal(normalizedPhoneNumber.toByteArray(Charsets.UTF_8))

        return listOf(
            version,
            BASE64_ENCODER.encodeToString(iv),
            BASE64_ENCODER.encodeToString(encrypted),
        ).joinToString(separator = ENCRYPTED_VALUE_SEPARATOR)
    }

    fun decrypt(encryptedPhoneNumber: String): String {
        val parts = encryptedPhoneNumber.split(ENCRYPTED_VALUE_SEPARATOR)
        if (parts.size != ENCRYPTED_VALUE_PART_COUNT || parts[0] != version) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        val iv = runCatching { BASE64_DECODER.decode(parts[1]) }
            .getOrElse { throw BusinessException(CommonErrorCode.INVALID_INPUT) }
        val encrypted = runCatching { BASE64_DECODER.decode(parts[2]) }
            .getOrElse { throw BusinessException(CommonErrorCode.INVALID_INPUT) }

        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(createAesKey(), AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )

        return runCatching {
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrElse {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    fun mask(normalizedPhoneNumber: String): String {
        if (!normalizedPhoneNumber.startsWith(KOREAN_MOBILE_PREFIX) || normalizedPhoneNumber.length != E164_KOREAN_MOBILE_LENGTH) {
            throw BusinessException(CommonErrorCode.INVALID_PHONE_NUMBER)
        }

        return "010-****-${normalizedPhoneNumber.takeLast(4)}"
    }

    private fun createAesKey(): ByteArray {
        return MessageDigest
            .getInstance(SHA_256_ALGORITHM)
            .digest(key.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val AES_ALGORITHM = "AES"
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
        private const val SHA_256_ALGORITHM = "SHA-256"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val MIN_KEY_LENGTH = 32
        private const val ENCRYPTED_VALUE_SEPARATOR = ":"
        private const val ENCRYPTED_VALUE_PART_COUNT = 3
        private const val KOREAN_MOBILE_PREFIX = "+8210"
        private const val E164_KOREAN_MOBILE_LENGTH = 13
        private val SECURE_RANDOM = SecureRandom()
        private val BASE64_ENCODER = Base64.getEncoder()
        private val BASE64_DECODER = Base64.getDecoder()
    }
}
