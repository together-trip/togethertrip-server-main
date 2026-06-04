package com.togethertrip.main.auth.client

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.auth.service.phone.SmsSender
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.phone.PhoneNumberNormalizer
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class SolapiSmsSender(
    private val webClientBuilder: WebClient.Builder,
    private val properties: SolapiSmsProperties,
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
) : SmsSender {

    override fun validateSendable() {
        validateProperties()
    }

    override fun sendVerificationCode(
        phoneNumber: String,
        code: String,
    ) {
        validateProperties()

        val request = SolapiSendRequest(
            messages = listOf(
                SolapiMessage(
                    to = phoneNumberNormalizer.toSolapiRecipient(phoneNumber),
                    from = properties.from,
                    text = "[TogetherTrip] 인증번호는 $code 입니다.",
                    type = "SMS",
                    country = "82",
                )
            )
        )

        try {
            webClientBuilder
                .baseUrl(properties.baseUrl)
                .build()
                .post()
                .uri("/messages/v4/send-many/detail")
                .header("Authorization", createAuthorizationHeader())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        } catch (exception: RuntimeException) {
            throw BusinessException(AuthErrorCode.SMS_SEND_FAILED)
        }
    }

    private fun validateProperties() {
        if (
            properties.apiKey.isBlank() ||
            properties.apiSecret.isBlank() ||
            properties.from.isBlank()
        ) {
            throw BusinessException(AuthErrorCode.SMS_CONFIGURATION_REQUIRED)
        }
    }

    private fun createAuthorizationHeader(): String {
        val dateTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val salt = UUID.randomUUID().toString().replace("-", "")
        val signature = createSignature(
            dateTime = dateTime,
            salt = salt,
        )

        return "HMAC-SHA256 apiKey=${properties.apiKey}, date=$dateTime, salt=$salt, signature=$signature"
    }

    private fun createSignature(
        dateTime: String,
        salt: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(
            SecretKeySpec(
                properties.apiSecret.toByteArray(Charsets.UTF_8),
                "HmacSHA256",
            )
        )

        return mac
            .doFinal("$dateTime$salt".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private data class SolapiSendRequest(
        val messages: List<SolapiMessage>,
    )

    private data class SolapiMessage(
        val to: String,
        val from: String,
        val text: String,
        val type: String,
        val country: String,
    )
}
