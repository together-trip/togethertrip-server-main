package com.togethertrip.main.global.phone

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class PhoneNumberHasher(
    @Value("\${phone.hash.key}")
    private val key: String,
    @Value("\${phone.hash.version:v1}")
    val version: String,
) {

    init {
        require(key.length >= MIN_KEY_LENGTH) {
            "phone.hash.key must be at least $MIN_KEY_LENGTH characters."
        }
        require(version.isNotBlank()) {
            "phone.hash.version must not be blank."
        }
    }

    fun hash(normalizedPhoneNumber: String): String {
        // 전화번호 HMAC hash 생성
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))

        return mac.doFinal(normalizedPhoneNumber.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val MIN_KEY_LENGTH = 32
    }
}
