package com.togethertrip.main.global.phone

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import org.springframework.stereotype.Component

@Component
class PhoneNumberNormalizer {

    fun normalize(phoneNumber: String): String {
        val compact = phoneNumber
            .trim()
            .replace("-", "")
            .replace(" ", "")

        return when {
            KOREAN_MOBILE_LOCAL.matches(compact) -> "+82${compact.substring(1)}"
            KOREAN_MOBILE_E164.matches(compact) -> compact
            else -> throw BusinessException(CommonErrorCode.INVALID_PHONE_NUMBER)
        }
    }

    fun toSolapiRecipient(phoneNumber: String): String {
        val normalized = normalize(phoneNumber)

        return "0${normalized.removePrefix("+82")}"
    }

    companion object {
        private val KOREAN_MOBILE_LOCAL = Regex("^010\\d{8}$")
        private val KOREAN_MOBILE_E164 = Regex("^\\+8210\\d{8}$")
    }
}
