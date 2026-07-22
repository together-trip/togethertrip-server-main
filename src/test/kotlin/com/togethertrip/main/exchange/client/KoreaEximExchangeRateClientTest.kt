package com.togethertrip.main.exchange.client

import com.togethertrip.main.exchange.config.ExchangeRateProperties
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class KoreaEximExchangeRateClientTest {

    @Test
    fun `auth key가 비어 있으면 HTTP 호출 전에 설정 오류를 반환한다`() {
        val calls = AtomicInteger()
        val properties = properties(authKey = "")
        val client = client(properties) {
            calls.incrementAndGet()
            response(HttpStatus.INTERNAL_SERVER_ERROR)
        }

        assertFailsWith<KoreaEximExchangeRateException> {
            client.fetchRates(LocalDate.parse("2026-07-01"))
        }
        assertEquals(0, calls.get())
    }

    @Test
    fun `성공 응답은 날짜 auth key data code query와 환율 row를 반환한다`() {
        val requestRef = AtomicReference<ClientRequest>()
        val properties = properties()
        val client = client(properties) { request ->
            requestRef.set(request)
            jsonResponse(
                """
                [
                  {"result":1,"cur_unit":"USD","deal_bas_r":"1,380.50"},
                  {"result":1,"cur_unit":"JPY(100)","deal_bas_r":"950.25"}
                ]
                """.trimIndent()
            )
        }

        val result = assertIs<KoreaEximExchangeRateFetchResult.Success>(
            client.fetchRates(LocalDate.parse("2026-07-01"))
        )

        assertEquals(2, result.responses.size)
        assertEquals("USD", result.responses[0].currencyUnit)
        assertEquals("1,380.50", result.responses[0].dealBaseRate)
        val query = requireNotNull(requestRef.get().url().query)
        assertEquals(true, query.contains("authkey=test-auth-key"))
        assertEquals(true, query.contains("searchdate=20260701"))
        assertEquals(true, query.contains("data=AP01"))
    }

    @Test
    fun `빈 배열 응답은 NoData로 처리한다`() {
        val client = client(properties()) { jsonResponse("[]") }

        assertEquals(
            KoreaEximExchangeRateFetchResult.NoData,
            client.fetchRates(LocalDate.parse("2026-07-01")),
        )
    }

    @Test
    fun `result code 3은 INVALID_AUTH_KEY 실패로 처리한다`() {
        val client = client(properties()) { jsonResponse("[{\"result\":3}]") }

        val result = assertIs<KoreaEximExchangeRateFetchResult.Failed>(
            client.fetchRates(LocalDate.parse("2026-07-01"))
        )

        assertEquals(KoreaEximExchangeRateResultCode.INVALID_AUTH_KEY, result.resultCode)
    }

    @Test
    fun `알 수 없는 result code는 UNKNOWN 실패로 처리한다`() {
        val client = client(properties()) { jsonResponse("[{\"result\":999}]") }

        val result = assertIs<KoreaEximExchangeRateFetchResult.Failed>(
            client.fetchRates(LocalDate.parse("2026-07-01"))
        )

        assertEquals(KoreaEximExchangeRateResultCode.UNKNOWN, result.resultCode)
    }

    @Test
    fun `result가 없는 row는 응답 자체를 Success로 전달해 importer validation에 맡긴다`() {
        val client = client(properties()) {
            jsonResponse("[{\"cur_unit\":\"USD\",\"deal_bas_r\":\"1380.00\"}]")
        }

        val result = assertIs<KoreaEximExchangeRateFetchResult.Success>(
            client.fetchRates(LocalDate.parse("2026-07-01"))
        )

        assertEquals(null, result.responses.single().result)
    }

    @Test
    fun `HTTP 500은 WebClient 오류로 전달되어 import service의 Error 결과 경계로 넘어간다`() {
        val client = client(properties()) { response(HttpStatus.INTERNAL_SERVER_ERROR, "failed") }

        assertFailsWith<WebClientResponseException> {
            client.fetchRates(LocalDate.parse("2026-07-01"))
        }
    }

    private fun properties(authKey: String = "test-auth-key"): ExchangeRateProperties {
        return ExchangeRateProperties().apply {
            koreaExim.baseUrl = "https://exchange.test"
            koreaExim.authKey = authKey
            koreaExim.dataCode = "AP01"
            koreaExim.timeout = Duration.ofSeconds(2)
        }
    }

    private fun client(
        properties: ExchangeRateProperties,
        responder: (ClientRequest) -> ClientResponse,
    ): KoreaEximExchangeRateClient {
        val exchangeFunction = ExchangeFunction { request -> Mono.just(responder(request)) }
        return KoreaEximExchangeRateClient(
            webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction),
            properties = properties,
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
