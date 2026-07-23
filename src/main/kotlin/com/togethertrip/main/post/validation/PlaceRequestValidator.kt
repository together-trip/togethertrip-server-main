package com.togethertrip.main.post.validation

import com.togethertrip.main.post.dto.request.PlaceRequest
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.math.BigDecimal

class PlaceRequestValidator : ConstraintValidator<ValidPlace, PlaceRequest> {

    override fun isValid(
        value: PlaceRequest?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null) return true
        if ((value.placeName?.length ?: 0) > MAX_PLACE_NAME_LENGTH) return false

        val latitude = value.latitude
        val longitude = value.longitude
        if ((latitude == null) != (longitude == null)) return false
        if (latitude == null || longitude == null) return true
        if (value.placeName.isNullOrBlank()) return false

        return latitude in MIN_LATITUDE..MAX_LATITUDE &&
            longitude in MIN_LONGITUDE..MAX_LONGITUDE
    }

    companion object {
        private const val MAX_PLACE_NAME_LENGTH = 100
        private val MIN_LATITUDE = BigDecimal("-90")
        private val MAX_LATITUDE = BigDecimal("90")
        private val MIN_LONGITUDE = BigDecimal("-180")
        private val MAX_LONGITUDE = BigDecimal("180")
    }
}
