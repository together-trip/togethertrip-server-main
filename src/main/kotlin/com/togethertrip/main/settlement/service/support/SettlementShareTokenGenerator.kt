package com.togethertrip.main.settlement.service.support

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

@Component
class SettlementShareTokenGenerator {

    fun generate(): String {
        val bytes = ByteArray(SHARE_TOKEN_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    private companion object {
        private const val SHARE_TOKEN_BYTE_LENGTH = 32
        private val secureRandom = SecureRandom()
    }
}
