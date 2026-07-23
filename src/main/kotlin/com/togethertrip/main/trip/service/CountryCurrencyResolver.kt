package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.trip.exception.TripErrorCode
import org.springframework.stereotype.Component

@Component
class CountryCurrencyResolver {

    fun resolve(countryCode: String): String {
        val normalizedCountryCode = countryCode.trim().uppercase()

        return countryCurrencies[normalizedCountryCode]
            ?: throw BusinessException(TripErrorCode.UNSUPPORTED_TRIP_COUNTRY_CURRENCY)
    }

    companion object {
        private val countryCurrencies = mapOf(
            "KR" to "KRW",
            "JP" to "JPY",
            "VN" to "VND",
            "TH" to "THB",
            "US" to "USD",
            "CN" to "CNY",
            "TW" to "TWD",
            "HK" to "HKD",
            "SG" to "SGD",
            "MY" to "MYR",
            "PH" to "PHP",
            "ID" to "IDR",
            "FR" to "EUR",
            "DE" to "EUR",
            "IT" to "EUR",
            "ES" to "EUR",
        )
    }
}
