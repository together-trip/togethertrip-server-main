package com.togethertrip.main.post.validation

import com.togethertrip.main.post.dto.request.CreatePostRequest
import jakarta.validation.Validation
import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigDecimal

class PlaceRequestValidatorTest {

    @Test
    fun `장소와 좌표가 모두 없으면 유효하다`() {
        assertTrue(validator.validate(CreatePostRequest()).isEmpty())
        assertTrue(PlaceRequestValidator().isValid(null, mock(ConstraintValidatorContext::class.java)))
    }

    @Test
    fun `장소명만 있으면 기존 텍스트 입력과 호환된다`() {
        assertTrue(validator.validate(CreatePostRequest(placeName = "도쿄역")).isEmpty())
    }

    @Test
    fun `장소명과 유효한 좌표 쌍이 있으면 유효하다`() {
        val request = CreatePostRequest(
            placeName = "도쿄역",
            latitude = BigDecimal("35.681236"),
            longitude = BigDecimal("139.767125"),
        )

        assertTrue(validator.validate(request).isEmpty())
    }

    @Test
    fun `좌표가 한쪽만 있으면 유효하지 않다`() {
        val request = CreatePostRequest(
            placeName = "도쿄역",
            latitude = BigDecimal("35.681236"),
        )

        assertFalse(validator.validate(request).isEmpty())
        assertFalse(
            validator.validate(
                CreatePostRequest(
                    placeName = "도쿄역",
                    longitude = BigDecimal("139.767125"),
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun `좌표가 있는데 장소명이 비어 있으면 유효하지 않다`() {
        val request = CreatePostRequest(
            latitude = BigDecimal("35.681236"),
            longitude = BigDecimal("139.767125"),
        )

        assertFalse(validator.validate(request).isEmpty())
        assertFalse(
            validator.validate(
                CreatePostRequest(
                    placeName = "   ",
                    latitude = BigDecimal("35.681236"),
                    longitude = BigDecimal("139.767125"),
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun `위도나 경도가 범위를 벗어나면 유효하지 않다`() {
        val invalidLatitude = CreatePostRequest(
            placeName = "잘못된 위치",
            latitude = BigDecimal("90.0000001"),
            longitude = BigDecimal("0"),
        )
        val invalidLongitude = CreatePostRequest(
            placeName = "잘못된 위치",
            latitude = BigDecimal("0"),
            longitude = BigDecimal("-180.0000001"),
        )
        val invalidNegativeLatitude = CreatePostRequest(
            placeName = "잘못된 위치",
            latitude = BigDecimal("-90.0000001"),
            longitude = BigDecimal("0"),
        )
        val invalidPositiveLongitude = CreatePostRequest(
            placeName = "잘못된 위치",
            latitude = BigDecimal("0"),
            longitude = BigDecimal("180.0000001"),
        )

        assertFalse(validator.validate(invalidLatitude).isEmpty())
        assertFalse(validator.validate(invalidLongitude).isEmpty())
        assertFalse(validator.validate(invalidNegativeLatitude).isEmpty())
        assertFalse(validator.validate(invalidPositiveLongitude).isEmpty())
    }

    @Test
    fun `위도와 경도의 경계값은 유효하다`() {
        val boundaries = listOf(
            CreatePostRequest(
                placeName = "북동 경계",
                latitude = BigDecimal("90"),
                longitude = BigDecimal("180"),
            ),
            CreatePostRequest(
                placeName = "남서 경계",
                latitude = BigDecimal("-90"),
                longitude = BigDecimal("-180"),
            ),
        )

        boundaries.forEach { request ->
            assertTrue(validator.validate(request).isEmpty())
        }
    }

    @Test
    fun `장소명이 100자를 넘으면 유효하지 않다`() {
        assertFalse(
            validator.validate(CreatePostRequest(placeName = "가".repeat(101))).isEmpty(),
        )
    }

    companion object {
        private val factory = Validation.buildDefaultValidatorFactory()
        private val validator = factory.validator

        @JvmStatic
        @AfterAll
        fun closeValidatorFactory() {
            factory.close()
        }
    }
}
