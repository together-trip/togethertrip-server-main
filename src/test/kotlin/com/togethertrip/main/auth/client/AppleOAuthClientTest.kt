package com.togethertrip.main.auth.client

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppleOAuthClientTest {
    @Test
    fun `authorization code를 교환하고 refresh token을 revoke한다`() {
        val requests = CopyOnWriteArrayList<ClientRequest>()
        val client = client { request ->
            requests += request
            if (request.url().path.endsWith("/auth/token")) {
                jsonResponse(
                    """
                    {
                      "access_token": "apple-access",
                      "refresh_token": "apple-refresh",
                      "id_token": "apple-id-token"
                    }
                    """.trimIndent()
                )
            } else {
                response(HttpStatus.OK)
            }
        }

        val tokens = client.exchangeAuthorizationCode("authorization-code")
        client.revoke("apple-refresh")

        assertEquals("apple-access", tokens.accessToken)
        assertEquals("apple-refresh", tokens.refreshToken)
        assertEquals("apple-id-token", tokens.idToken)
        assertEquals(listOf("/auth/token", "/auth/revoke"), requests.map { it.url().path })
        requests.forEach {
            assertEquals(MediaType.APPLICATION_FORM_URLENCODED, it.headers().contentType)
        }
    }

    @Test
    fun `Apple token endpoint 장애를 인증 실패로 변환한다`() {
        val client = client { response(HttpStatus.UNAUTHORIZED, "invalid_client") }

        val exception = assertFailsWith<BusinessException> {
            client.exchangeAuthorizationCode("invalid-code")
        }

        assertEquals(AuthErrorCode.APPLE_AUTHORIZATION_FAILED, exception.errorCode)
    }

    @Test
    fun `Apple 설정이 비활성화되면 외부 호출 전에 실패한다`() {
        val client = AppleOAuthClient(
            webClientBuilder = WebClient.builder(),
            properties = AppleOAuthProperties(enabled = false),
        )

        val exception = assertFailsWith<BusinessException> {
            client.exchangeAuthorizationCode("authorization-code")
        }

        assertEquals(AuthErrorCode.APPLE_AUTHORIZATION_FAILED, exception.errorCode)
    }

    @Test
    fun `Apple 필수 설정이 비어 있으면 외부 호출 전에 실패한다`() {
        val client = AppleOAuthClient(
            webClientBuilder = WebClient.builder(),
            properties = AppleOAuthProperties(enabled = true),
        )

        val exception = assertFailsWith<BusinessException> {
            client.revoke("refresh-token")
        }

        assertEquals(AuthErrorCode.APPLE_AUTHORIZATION_FAILED, exception.errorCode)
    }

    private fun client(responder: (ClientRequest) -> ClientResponse): AppleOAuthClient {
        val keyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val privateKey = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val exchange = ExchangeFunction { request -> Mono.just(responder(request)) }
        return AppleOAuthClient(
            webClientBuilder = WebClient.builder().exchangeFunction(exchange),
            properties = AppleOAuthProperties(
                enabled = true,
                clientId = "com.togethertrip.app",
                teamId = "TEAM123",
                keyId = "KEY123",
                privateKey = privateKey,
                tokenEncryptionKey = "configured",
            ),
            clock = Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC),
        )
    }

    private fun response(status: HttpStatus, body: String? = null): ClientResponse {
        val builder = ClientResponse.create(status)
        if (body != null) builder.body(body)
        return builder.build()
    }

    private fun jsonResponse(body: String): ClientResponse {
        return ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .build()
    }
}
