package com.togethertrip.main.place.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.place.dto.PlaceDetailResponse
import com.togethertrip.main.place.dto.PlaceSuggestionResponse
import com.togethertrip.main.place.service.PlaceAutocompleteCommand
import com.togethertrip.main.place.service.PlaceDetailCommand
import com.togethertrip.main.place.service.PlaceReverseGeocodeCommand
import com.togethertrip.main.place.service.PlaceService
import com.togethertrip.main.user.domain.UserRole
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal

class PlaceControllerValidationTest {

    @Test
    fun `인터페이스의 장소 검색 제약을 구현 메서드에서 중복 선언하지 않는다`() {
        val controller = PlaceController(mock(PlaceService::class.java))
        val method = PlaceController::class.java.getMethod(
            "autocomplete",
            AuthUser::class.java,
            Long::class.javaPrimitiveType,
            String::class.java,
            String::class.java,
            String::class.java,
        )

        val violations = assertDoesNotThrow<Set<ConstraintViolation<PlaceController>>> {
            executableValidator.validateParameters(
                controller,
                method,
                arrayOf(mock(AuthUser::class.java), 1L, "a", null, "ko"),
            )
        }

        assertTrue(violations.any { it.propertyPath.toString().endsWith("query") })
    }

    @Test
    fun `장소 endpoint는 인증 사용자와 요청 값을 service command로 전달한다`() {
        val service = mock(PlaceService::class.java)
        val controller = PlaceController(service)
        val authUser = AuthUser(7L, UserRole.USER)
        val suggestions = listOf(PlaceSuggestionResponse("place-1", "도쿄역", "일본 도쿄도"))
        val detail = PlaceDetailResponse(
            "place-1",
            "도쿄역",
            "일본 도쿄도",
            BigDecimal("35.681236"),
            BigDecimal("139.767125"),
        )
        `when`(
            service.autocomplete(PlaceAutocompleteCommand(7L, "도쿄역", "session-1", "ko")),
        ).thenReturn(suggestions)
        `when`(
            service.getPlace(PlaceDetailCommand(7L, "place-1", "session-1", "ko")),
        ).thenReturn(detail)
        `when`(
            service.reverseGeocode(
                PlaceReverseGeocodeCommand(
                    7L,
                    BigDecimal("35.681236"),
                    BigDecimal("139.767125"),
                    "ko",
                ),
            ),
        ).thenReturn(detail)

        val autocomplete = controller.autocomplete(authUser, 10L, "도쿄역", "session-1", "ko")
        val getPlace = controller.getPlace(authUser, 10L, "place-1", "session-1", "ko")
        val reverse = controller.reverseGeocode(
            authUser,
            10L,
            BigDecimal("35.681236"),
            BigDecimal("139.767125"),
            "ko",
        )

        assertEquals(suggestions, autocomplete.data)
        assertEquals(detail, getPlace.data)
        assertEquals(detail, reverse.data)
    }

    companion object {
        private val validatorFactory = Validation.buildDefaultValidatorFactory()
        private val executableValidator = validatorFactory.validator.forExecutables()

        @JvmStatic
        @AfterAll
        fun closeValidatorFactory() {
            validatorFactory.close()
        }
    }
}
