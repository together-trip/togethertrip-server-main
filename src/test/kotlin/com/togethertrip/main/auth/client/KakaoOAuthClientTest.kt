package com.togethertrip.main.auth.client

import com.togethertrip.main.auth.domain.OAuthProvider
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KakaoOAuthClientTest {

    @Test
    fun `local test token은 외부 호출 없이 지정 ID의 사용자 정보를 만든다`() {
        val calls = AtomicInteger()
        val client = client(localTestEnabled = true) {
            calls.incrementAndGet()
            response(HttpStatus.INTERNAL_SERVER_ERROR)
        }

        val result = client.getUserInfo("local-test:jaewan")

        assertEquals(OAuthProvider.KAKAO, result.provider)
        assertEquals("local-test-jaewan", result.providerUserId)
        assertEquals("로컬 테스트 jaewan", result.nickname)
        assertNull(result.profileImageUrl)
        assertEquals(0, calls.get())
    }

    @Test
    fun `빈 local test ID는 swagger fallback을 사용한다`() {
        val client = client(localTestEnabled = true) { response(HttpStatus.INTERNAL_SERVER_ERROR) }

        val result = client.getUserInfo("local-test:")

        assertEquals("local-test-swagger", result.providerUserId)
        assertEquals("로컬 테스트 swagger", result.nickname)
    }

    @Test
    fun `Kakao account profile을 properties보다 우선하고 Bearer header를 전송한다`() {
        val requestRef = AtomicReference<ClientRequest>()
        val client = client(localTestEnabled = false) { request ->
            requestRef.set(request)
            jsonResponse(
                """
                {
                  "id": 12345,
                  "kakao_account": {"profile": {"nickname": "account-name", "profile_image_url": "account.png"}},
                  "properties": {"nickname": "property-name", "profile_image": "property.png"}
                }
                """.trimIndent()
            )
        }

        val result = client.getUserInfo("access-token")

        assertEquals("12345", result.providerUserId)
        assertEquals("account-name", result.nickname)
        assertEquals("account.png", result.profileImageUrl)
        assertEquals("Bearer access-token", requestRef.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
        assertEquals("/v2/user/me", requestRef.get().url().path)
    }

    @Test
    fun `Kakao account profile 값이 없으면 properties 값을 사용한다`() {
        val client = client(localTestEnabled = false) {
            jsonResponse(
                """
                {
                  "id": 55,
                  "kakao_account": {"profile": {}},
                  "properties": {"nickname": "fallback-name", "profile_image": "fallback.png"}
                }
                """.trimIndent()
            )
        }

        val result = client.getUserInfo("access-token")

        assertEquals("fallback-name", result.nickname)
        assertEquals("fallback.png", result.profileImageUrl)
    }

    @Test
    fun `Kakao 401 응답은 OAuth user info 실패로 변환한다`() {
        val client = client(localTestEnabled = false) { response(HttpStatus.UNAUTHORIZED, "unauthorized") }

        val exception = assertFailsWith<BusinessException> { client.getUserInfo("expired-token") }

        assertEquals(AuthErrorCode.OAUTH_USER_INFO_FAILED, exception.errorCode)
    }

    @Test
    fun `Kakao empty 200 응답은 OAuth user info 실패로 변환한다`() {
        val client = client(localTestEnabled = false) { response(HttpStatus.OK) }

        val exception = assertFailsWith<BusinessException> { client.getUserInfo("access-token") }

        assertEquals(AuthErrorCode.OAUTH_USER_INFO_FAILED, exception.errorCode)
    }

    @Test
    fun `Kakao malformed 200 응답은 OAuth user info 실패로 변환한다`() {
        val client = client(localTestEnabled = false) { jsonResponse("{not-json") }

        val exception = assertFailsWith<BusinessException> { client.getUserInfo("access-token") }

        assertEquals(AuthErrorCode.OAUTH_USER_INFO_FAILED, exception.errorCode)
    }

    private fun client(
        localTestEnabled: Boolean,
        responder: (ClientRequest) -> ClientResponse,
    ): KakaoOAuthClient {
        val exchangeFunction = ExchangeFunction { request -> Mono.just(responder(request)) }
        return KakaoOAuthClient(
            webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction),
            localTestEnabled = localTestEnabled,
        )
    }

    private fun response(status: HttpStatus, body: String? = null): ClientResponse {
        val builder = ClientResponse.create(status)
        if (body != null) {
            builder.body(body)
        }
        return builder.build()
    }

    private fun jsonResponse(body: String): ClientResponse {
        return ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .build()
    }
}
