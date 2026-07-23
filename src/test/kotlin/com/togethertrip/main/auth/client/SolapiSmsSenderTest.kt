package com.togethertrip.main.auth.client

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.phone.PhoneNumberNormalizer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SolapiSmsSenderTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `필수 Solapi 설정 누락은 전송 가능성 검사에서 각각 거부한다`() {
        val cases = listOf<(SolapiSmsProperties) -> Unit>(
            { it.apiKey = " " },
            { it.apiSecret = " " },
            { it.from = " " },
        )

        cases.forEach { mutate ->
            val properties = properties().also(mutate)
            val exception = assertBusinessException {
                sender(properties).validateSendable()
            }
            assertEquals(AuthErrorCode.SMS_CONFIGURATION_REQUIRED, exception.errorCode)
        }
    }

    @Test
    fun `인증번호 전송은 수신번호를 국내 형식으로 바꾸고 검증 가능한 HMAC header를 보낸다`() {
        val captured = mutableListOf<CapturedRequest>()
        startServer(captured, status = 200, body = "{\"groupId\":\"group-1\"}")
        val properties = properties()

        sender(properties).sendVerificationCode("+82 10-1234-5678", "654321")

        assertEquals(1, captured.size)
        val request = captured.single()
        assertEquals("/messages/v4/send-many/detail", request.path)
        assertTrue(request.body.contains("\"to\":\"01012345678\""))
        assertTrue(request.body.contains("\"from\":\"0212345678\""))
        assertTrue(request.body.contains("[TogetherTrip] 인증번호는 654321 입니다."))
        assertTrue(request.body.contains("\"type\":\"SMS\""))
        assertTrue(request.body.contains("\"country\":\"82\""))

        val fields = parseAuthorization(request.authorization)
        assertEquals("test-api-key", fields["apiKey"])
        assertEquals(32, fields.getValue("salt").length)
        assertEquals(
            hmacSha256(properties.apiSecret, fields.getValue("date") + fields.getValue("salt")),
            fields["signature"],
        )
    }

    @Test
    fun `Solapi HTTP 실패는 SMS 전송 실패 오류로 변환한다`() {
        startServer(mutableListOf(), status = 500, body = "{\"error\":\"provider down\"}")

        val exception = assertBusinessException {
            sender(properties()).sendVerificationCode("010-1234-5678", "123456")
        }

        assertEquals(AuthErrorCode.SMS_SEND_FAILED, exception.errorCode)
    }

    @Test
    fun `잘못된 전화번호는 provider 호출 전에 원래 입력 오류로 거부한다`() {
        val captured = mutableListOf<CapturedRequest>()
        startServer(captured, status = 200, body = "{}")

        val exception = assertBusinessException {
            sender(properties()).sendVerificationCode("javascript:alert(1)", "123456")
        }

        assertEquals(CommonErrorCode.INVALID_PHONE_NUMBER, exception.errorCode)
        assertEquals(0, captured.size)
    }

    private fun sender(properties: SolapiSmsProperties): SolapiSmsSender {
        return SolapiSmsSender(
            webClientBuilder = WebClient.builder(),
            properties = properties,
            phoneNumberNormalizer = PhoneNumberNormalizer(),
        )
    }

    private fun properties(): SolapiSmsProperties {
        return SolapiSmsProperties().apply {
            apiKey = "test-api-key"
            apiSecret = "test-api-secret"
            from = "0212345678"
            baseUrl = "http://127.0.0.1:${server?.address?.port ?: 1}"
        }
    }

    private fun startServer(
        captured: MutableList<CapturedRequest>,
        status: Int,
        body: String,
    ) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                captured += capture(exchange)
                val response = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }
    }

    private fun capture(exchange: HttpExchange): CapturedRequest {
        return CapturedRequest(
            path = exchange.requestURI.path,
            authorization = exchange.requestHeaders.getFirst("Authorization"),
            body = exchange.requestBody.use { it.readBytes() }.toString(Charsets.UTF_8),
        )
    }

    private fun parseAuthorization(header: String?): Map<String, String> {
        assertNotNull(header)
        assertTrue(header.startsWith("HMAC-SHA256 "))
        return header.removePrefix("HMAC-SHA256 ")
            .split(", ")
            .associate { field ->
                val (key, value) = field.split("=", limit = 2)
                key to value
            }
    }

    private fun hmacSha256(secret: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try {
            block()
            throw AssertionError("BusinessException이 발생해야 합니다.")
        } catch (exception: BusinessException) {
            exception
        }
    }

    private data class CapturedRequest(
        val path: String,
        val authorization: String?,
        val body: String,
    )
}
