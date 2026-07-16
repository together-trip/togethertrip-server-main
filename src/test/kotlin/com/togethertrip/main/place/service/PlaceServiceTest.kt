package com.togethertrip.main.place.service

import com.togethertrip.main.place.client.PlaceSearchClient
import com.togethertrip.main.place.dto.PlaceDetailResponse
import com.togethertrip.main.place.dto.PlaceSuggestionResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PlaceServiceTest {

    @Test
    fun `검색어와 세션 토큰을 정규화해 client에 전달한다`() {
        val client = FakePlaceSearchClient()
        val rateLimiter = FakePlaceRequestRateLimiter()
        val service = PlaceService(client, rateLimiter)

        val result = service.autocomplete(
            PlaceAutocompleteCommand(7, "  도쿄역  ", "  session-1  ", "ko"),
        )

        assertEquals("도쿄역", client.query)
        assertEquals("session-1", client.sessionToken)
        assertEquals(client.suggestions, result)
        assertEquals(PlaceOperation.AUTOCOMPLETE, rateLimiter.operation)
    }

    @Test
    fun `빈 세션 토큰은 null로 전달한다`() {
        val client = FakePlaceSearchClient()
        val rateLimiter = FakePlaceRequestRateLimiter()
        val service = PlaceService(client, rateLimiter)

        service.getPlace(PlaceDetailCommand(7, "place-1", "   ", "ko"))

        assertNull(client.sessionToken)
        assertEquals("place-1", client.placeId)
        assertEquals(PlaceOperation.DETAIL, rateLimiter.operation)
    }

    @Test
    fun `세션 토큰이 null이면 자동완성 client에도 null로 전달한다`() {
        val client = FakePlaceSearchClient()
        val service = PlaceService(client, FakePlaceRequestRateLimiter())

        service.autocomplete(PlaceAutocompleteCommand(7, "도쿄역", null, "ko"))

        assertNull(client.sessionToken)
    }

    @Test
    fun `장소 상세의 세션 토큰은 trim해서 client에 전달한다`() {
        val client = FakePlaceSearchClient()
        val service = PlaceService(client, FakePlaceRequestRateLimiter())

        service.getPlace(PlaceDetailCommand(7, "place-1", "  session-1  ", "ko"))

        assertEquals("session-1", client.sessionToken)
    }

    @Test
    fun `역지오코딩 좌표를 client에 전달한다`() {
        val client = FakePlaceSearchClient()
        val rateLimiter = FakePlaceRequestRateLimiter()
        val service = PlaceService(client, rateLimiter)

        val result = service.reverseGeocode(
            PlaceReverseGeocodeCommand(7, BigDecimal.ONE, BigDecimal.TEN, "en"),
        )

        assertEquals(BigDecimal.ONE, client.latitude)
        assertEquals(BigDecimal.TEN, client.longitude)
        assertEquals("en", client.languageCode)
        assertEquals(client.detail, result)
        assertEquals(PlaceOperation.REVERSE_GEOCODE, rateLimiter.operation)
    }

    @Test
    fun `장소 command 문자열은 검색어 토큰 좌표를 노출하지 않는다`() {
        val autocomplete = PlaceAutocompleteCommand(7, "도쿄역", "secret-session", "ko").toString()
        val detail = PlaceDetailCommand(7, "secret-place-id", "secret-session", "ko").toString()
        val reverse = PlaceReverseGeocodeCommand(
            7,
            BigDecimal("35.681236"),
            BigDecimal("139.767125"),
            "ko",
        ).toString()

        assertFalse(autocomplete.contains("도쿄역"))
        assertFalse(autocomplete.contains("secret-session"))
        assertFalse(detail.contains("secret-place-id"))
        assertFalse(reverse.contains("35.681236"))
        assertFalse(reverse.contains("139.767125"))
        assertTrue(PlaceAutocompleteCommand(7, "도쿄역", null, "ko").toString().contains("hasSessionToken=false"))
        assertTrue(PlaceDetailCommand(7, "place-1", "   ", "ko").toString().contains("hasSessionToken=false"))
    }

    private class FakePlaceRequestRateLimiter : PlaceRequestRateLimiter {
        var userId: Long? = null
        var operation: PlaceOperation? = null

        override fun validate(userId: Long, operation: PlaceOperation) {
            this.userId = userId
            this.operation = operation
        }
    }

    private class FakePlaceSearchClient : PlaceSearchClient {
        var query: String? = null
        var placeId: String? = null
        var sessionToken: String? = null
        var latitude: BigDecimal? = null
        var longitude: BigDecimal? = null
        var languageCode: String? = null
        val suggestions = listOf(PlaceSuggestionResponse("place-1", "도쿄역", "일본 도쿄도"))
        val detail = PlaceDetailResponse("place-1", "도쿄역", "일본 도쿄도", BigDecimal.ONE, BigDecimal.TEN)

        override fun autocomplete(
            query: String,
            sessionToken: String?,
            languageCode: String,
        ): List<PlaceSuggestionResponse> {
            this.query = query
            this.sessionToken = sessionToken
            this.languageCode = languageCode
            return suggestions
        }

        override fun getPlace(
            placeId: String,
            sessionToken: String?,
            languageCode: String,
        ): PlaceDetailResponse {
            this.placeId = placeId
            this.sessionToken = sessionToken
            this.languageCode = languageCode
            return detail
        }

        override fun reverseGeocode(
            latitude: BigDecimal,
            longitude: BigDecimal,
            languageCode: String,
        ): PlaceDetailResponse {
            this.latitude = latitude
            this.longitude = longitude
            this.languageCode = languageCode
            return detail
        }
    }
}
