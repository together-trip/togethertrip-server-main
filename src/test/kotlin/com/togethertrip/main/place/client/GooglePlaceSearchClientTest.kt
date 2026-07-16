package com.togethertrip.main.place.client

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.place.exception.PlaceErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.ArrayDeque

class GooglePlaceSearchClientTest {

    @Test
    fun `자동완성 응답을 내부 장소 후보로 변환한다`() {
        val exchange = QueueExchangeFunction(
            jsonResponse(
                """
                {
                  "suggestions": [
                    {
                      "placePrediction": {
                        "placeId": "place-1",
                        "structuredFormat": {
                          "mainText": {"text": "도쿄역"},
                          "secondaryText": {"text": "일본 도쿄도"}
                        }
                      }
                    },
                    {"placePrediction": {"placeId": "", "structuredFormat": {}}}
                  ]
                }
                """.trimIndent(),
            ),
        )
        val client = client(exchange)

        val results = client.autocomplete("도쿄역", "session-1", "ko")

        assertEquals(1, results.size)
        assertEquals("place-1", results.single().placeId)
        assertEquals("도쿄역", results.single().name)
        assertEquals("일본 도쿄도", results.single().address)
        assertEquals("/v1/places:autocomplete", exchange.requests.single().url().path)
        assertEquals("test-key", exchange.requests.single().headers().getFirst("X-Goog-Api-Key"))
    }

    @Test
    fun `장소 상세 응답에서 이름 주소 좌표를 반환한다`() {
        val exchange = QueueExchangeFunction(
            jsonResponse(
                """
                {
                  "id": "place-1",
                  "displayName": {"text": "도쿄역"},
                  "formattedAddress": "일본 도쿄도 지요다구",
                  "location": {"latitude": 35.681236, "longitude": 139.767125}
                }
                """.trimIndent(),
            ),
        )

        val result = client(exchange).getPlace("place-1", "session-1", "ko")

        assertEquals("place-1", result.placeId)
        assertEquals("도쿄역", result.name)
        assertEquals(BigDecimal("35.681236"), result.latitude)
        assertTrue(exchange.requests.single().url().query.orEmpty().contains("sessionToken=session-1"))
    }

    @Test
    fun `장소 상세 주소가 없으면 이름을 주소로 사용하고 응답 id가 없으면 요청 id를 사용한다`() {
        val exchange = QueueExchangeFunction(
            jsonResponse(
                """
                {
                  "displayName": {"text": "핀 위치"},
                  "location": {"latitude": 0, "longitude": 0}
                }
                """.trimIndent(),
            ),
        )

        val result = client(exchange).getPlace("fallback-id", null, "ko")

        assertEquals("fallback-id", result.placeId)
        assertEquals("핀 위치", result.address)
    }

    @Test
    fun `역지오코딩 응답을 선택 가능한 장소로 변환한다`() {
        val exchange = QueueExchangeFunction(
            jsonResponse(
                """
                {
                  "status": "OK",
                  "results": [
                    {
                      "place_id": "reverse-1",
                      "formatted_address": "일본 도쿄도 지요다구",
                      "address_components": [{"long_name": "도쿄역"}]
                    }
                  ]
                }
                """.trimIndent(),
            ),
            jsonResponse(
                """
                {
                  "id": "reverse-1",
                  "displayName": {"text": "도쿄역"},
                  "formattedAddress": "일본 도쿄도 지요다구",
                  "location": {"latitude": 35.681300, "longitude": 139.767200}
                }
                """.trimIndent(),
            ),
        )

        val result = client(exchange).reverseGeocode(
            BigDecimal("35.681236"),
            BigDecimal("139.767125"),
            "ko",
        )

        assertEquals("reverse-1", result.placeId)
        assertEquals("도쿄역", result.name)
        assertEquals("일본 도쿄도 지요다구", result.address)
        assertEquals(BigDecimal("35.681236"), result.latitude)
        assertEquals(BigDecimal("139.767125"), result.longitude)
        assertTrue(exchange.requests.first().url().query.orEmpty().contains("latlng=35.681236"))
        assertEquals("/v1/places/reverse-1", exchange.requests.last().url().path)
    }

    @Test
    fun `장소 상세 조회가 실패하면 번지 대신 전체 주소를 장소명으로 사용한다`() {
        val exchange = QueueExchangeFunction(
            jsonResponse(
                """
                {
                  "status": "OK",
                  "results": [
                    {
                      "place_id": "reverse-1",
                      "formatted_address": "대한민국 서울특별시 중구 남창동 9-28",
                      "address_components": [{"long_name": "9-28"}]
                    }
                  ]
                }
                """.trimIndent(),
            ),
            ClientResponse.create(HttpStatus.BAD_GATEWAY).build(),
        )

        val result = client(exchange).reverseGeocode(
            BigDecimal("37.5594"),
            BigDecimal("126.9788"),
            "ko",
        )

        assertEquals("대한민국 서울특별시 중구 남창동 9-28", result.name)
        assertEquals("대한민국 서울특별시 중구 남창동 9-28", result.address)
    }

    @Test
    fun `역지오코딩 결과가 없으면 장소 없음 오류를 반환한다`() {
        val exchange = QueueExchangeFunction(jsonResponse("""{"status":"ZERO_RESULTS"}"""))

        val exception = assertThrows(BusinessException::class.java) {
            client(exchange).reverseGeocode(BigDecimal.ZERO, BigDecimal.ZERO, "ko")
        }

        assertEquals(PlaceErrorCode.PLACE_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `provider 오류 상태와 HTTP 실패를 검색 실패로 변환한다`() {
        val providerError = QueueExchangeFunction(jsonResponse("""{"status":"REQUEST_DENIED"}"""))
        val httpError = QueueExchangeFunction(ClientResponse.create(HttpStatus.BAD_GATEWAY).build())

        val providerException = assertThrows(BusinessException::class.java) {
            client(providerError).reverseGeocode(BigDecimal.ZERO, BigDecimal.ZERO, "ko")
        }
        val httpException = assertThrows(BusinessException::class.java) {
            client(httpError).autocomplete("도쿄역", null, "ko")
        }

        assertEquals(PlaceErrorCode.PLACE_SEARCH_FAILED, providerException.errorCode)
        assertEquals(PlaceErrorCode.PLACE_SEARCH_FAILED, httpException.errorCode)
    }

    @Test
    fun `API 키가 없으면 외부 요청 없이 설정 오류를 반환한다`() {
        val properties = GooglePlacesProperties().apply { apiKey = "" }
        val exchange = QueueExchangeFunction()
        val client = GooglePlaceSearchClient(
            WebClient.builder().exchangeFunction(exchange),
            properties,
        )

        val exception = assertThrows(BusinessException::class.java) {
            client.autocomplete("도쿄역", null, "ko")
        }

        assertEquals(PlaceErrorCode.PLACE_SEARCH_NOT_CONFIGURED, exception.errorCode)
        assertTrue(exchange.requests.isEmpty())
    }

    @Test
    fun `자동완성 body가 비어 있으면 빈 후보 목록을 반환한다`() {
        val exchange = QueueExchangeFunction(jsonResponse("{}"))

        val result = client(exchange).autocomplete("도쿄역", null, "ko")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `자동완성 보조 주소가 없으면 null 주소의 유효한 후보를 반환한다`() {
        val exchange = QueueExchangeFunction(
            jsonResponse(
                """
                {
                  "suggestions": [{
                    "placePrediction": {
                      "placeId": "place-1",
                      "structuredFormat": {"mainText": {"text": "도쿄역"}}
                    }
                  }]
                }
                """.trimIndent(),
            ),
        )

        val result = client(exchange).autocomplete("도쿄역", null, "ko").single()

        assertEquals("도쿄역", result.name)
        assertEquals(null, result.address)
    }

    @Test
    fun `자동완성 응답 body가 없거나 필수 예측 필드가 없으면 후보에서 제외한다`() {
        val noBody = QueueExchangeFunction(ClientResponse.create(HttpStatus.NO_CONTENT).build())
        val sparsePredictions = QueueExchangeFunction(
            jsonResponse(
                """
                {
                  "suggestions": [
                    {},
                    {"placePrediction": {}},
                    {"placePrediction": {"placeId": "place-1"}},
                    {
                      "placePrediction": {
                        "placeId": "place-2",
                        "structuredFormat": {}
                      }
                    },
                    {
                      "placePrediction": {
                        "placeId": "place-3",
                        "structuredFormat": {"mainText": {}}
                      }
                    },
                    {
                      "placePrediction": {
                        "placeId": "place-4",
                        "structuredFormat": {"mainText": {"text": ""}}
                      }
                    },
                    {
                      "placePrediction": {
                        "placeId": "place-5",
                        "structuredFormat": {"mainText": {"text": "   "}}
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertTrue(client(noBody).autocomplete("도쿄", null, "ko").isEmpty())
        assertTrue(client(sparsePredictions).autocomplete("도쿄", null, "ko").isEmpty())
    }

    @Test
    fun `장소 상세 body 이름 좌표 중 필수 값이 없으면 장소 없음 오류를 반환한다`() {
        val responses = listOf(
            ClientResponse.create(HttpStatus.NO_CONTENT).build(),
            jsonResponse("""{"displayName":{"text":""},"location":{"latitude":1,"longitude":2}}"""),
            jsonResponse("""{"displayName":{"text":"도쿄역"}}"""),
        )

        responses.forEach { response ->
            val exception = assertThrows(BusinessException::class.java) {
                client(QueueExchangeFunction(response)).getPlace("place-1", null, "ko")
            }
            assertEquals(PlaceErrorCode.PLACE_NOT_FOUND, exception.errorCode)
        }
    }

    @Test
    fun `장소 상세의 빈 세션과 빈 주소는 query에서 제외하고 이름으로 주소를 대체한다`() {
        val exchange = QueueExchangeFunction(
            jsonResponse(
                """
                {
                  "displayName": {"text": "도쿄역"},
                  "formattedAddress": "",
                  "location": {"latitude": 35.681236, "longitude": 139.767125}
                }
                """.trimIndent(),
            ),
        )

        val result = client(exchange).getPlace("place-1", "   ", "ko")

        assertEquals("도쿄역", result.address)
        assertFalse(exchange.requests.single().url().query.orEmpty().contains("sessionToken"))
    }

    @Test
    fun `역지오코딩 body 결과 주소가 없으면 장소 없음 오류를 반환한다`() {
        val responses = listOf(
            ClientResponse.create(HttpStatus.NO_CONTENT).build(),
            jsonResponse("""{"status":"OK","results":[]}"""),
            jsonResponse("""{"status":"OK","results":[{}]}"""),
            jsonResponse("""{"status":"OK","results":[{"formatted_address":""}]}"""),
            jsonResponse("""{"status":"OK","results":[{"formatted_address":"   "}]}"""),
        )

        responses.forEach { response ->
            val exception = assertThrows(BusinessException::class.java) {
                client(QueueExchangeFunction(response)).reverseGeocode(BigDecimal.ONE, BigDecimal.TEN, "ko")
            }
            assertEquals(PlaceErrorCode.PLACE_NOT_FOUND, exception.errorCode)
        }
    }

    @Test
    fun `역지오코딩 place id가 없으면 전체 주소와 입력 좌표를 그대로 반환한다`() {
        val exchange = QueueExchangeFunction(
            jsonResponse(
                """
                {
                  "status": "OK",
                  "results": [{"formatted_address": "대한민국 서울특별시 중구"}]
                }
                """.trimIndent(),
            ),
        )

        val result = client(exchange).reverseGeocode(BigDecimal.ONE, BigDecimal.TEN, "ko")

        assertEquals(null, result.placeId)
        assertEquals("대한민국 서울특별시 중구", result.name)
        assertEquals(BigDecimal.ONE, result.latitude)
        assertEquals(BigDecimal.TEN, result.longitude)
        assertEquals(1, exchange.requests.size)
    }

    @Test
    fun `역지오코딩의 빈 place id는 상세 조회 없이 전체 주소를 반환한다`() {
        val exchange = QueueExchangeFunction(
            jsonResponse(
                """
                {
                  "status": "OK",
                  "results": [{
                    "place_id": "",
                    "formatted_address": "대한민국 서울특별시 중구"
                  }]
                }
                """.trimIndent(),
            ),
        )

        val result = client(exchange).reverseGeocode(BigDecimal.ONE, BigDecimal.TEN, "ko")

        assertEquals(null, result.placeId)
        assertEquals("대한민국 서울특별시 중구", result.name)
        assertEquals(1, exchange.requests.size)
    }

    @Test
    fun `외부 호출 블록의 비즈니스 오류는 원래 오류 코드로 유지한다`() {
        val expected = BusinessException(PlaceErrorCode.PLACE_NOT_FOUND)
        val exchange = ExchangeFunction { Mono.error(expected) }

        val actual = assertThrows(BusinessException::class.java) {
            client(exchange).autocomplete("도쿄", null, "ko")
        }

        assertEquals(PlaceErrorCode.PLACE_NOT_FOUND, actual.errorCode)
    }

    private fun client(exchange: ExchangeFunction): GooglePlaceSearchClient {
        val properties = GooglePlacesProperties().apply { apiKey = "test-key" }
        return GooglePlaceSearchClient(
            WebClient.builder().exchangeFunction(exchange),
            properties,
        )
    }

    private fun jsonResponse(body: String): ClientResponse {
        return ClientResponse.create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(body)
            .build()
    }

    private class QueueExchangeFunction(
        vararg responses: ClientResponse,
    ) : ExchangeFunction {
        private val responses = ArrayDeque(responses.toList())
        val requests = mutableListOf<ClientRequest>()

        override fun exchange(request: ClientRequest): Mono<ClientResponse> {
            requests += request
            return Mono.just(responses.removeFirst())
        }
    }
}
