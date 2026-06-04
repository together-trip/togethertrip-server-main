package com.togethertrip.main.global.phone

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PhoneNumberNormalizerTest {

    private val phoneNumberNormalizer = PhoneNumberNormalizer()

    @Test
    fun `한국 휴대폰 로컬 번호를 E164 형식으로 정규화한다`() {
        val phoneNumber = phoneNumberNormalizer.normalize("010-1234-5678")

        assertEquals("+821012345678", phoneNumber)
    }

    @Test
    fun `E164 형식의 한국 휴대폰 번호는 그대로 사용한다`() {
        val phoneNumber = phoneNumberNormalizer.normalize("+821012345678")

        assertEquals("+821012345678", phoneNumber)
    }

    @Test
    fun `SOLAPI 수신번호는 국내 010 형식으로 변환한다`() {
        val phoneNumber = phoneNumberNormalizer.toSolapiRecipient("+821012345678")

        assertEquals("01012345678", phoneNumber)
    }

    @Test
    fun `한국 휴대폰 번호가 아니면 실패한다`() {
        val exception = try {
            phoneNumberNormalizer.normalize("0212345678")
            throw AssertionError("BusinessException이 발생해야 합니다.")
        } catch (exception: BusinessException) {
            exception
        }

        assertEquals(CommonErrorCode.INVALID_PHONE_NUMBER, exception.errorCode)
    }
}
