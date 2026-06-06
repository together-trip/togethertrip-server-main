package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.trip.client.ExchangeRateClient
import com.togethertrip.main.trip.client.ExchangeRateQuote
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripCountry
import com.togethertrip.main.trip.domain.TripExchangeRate
import com.togethertrip.main.trip.dto.request.UpdateTripExchangeRateRequest
import com.togethertrip.main.trip.dto.response.TripExchangeRateResponse
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripCountryRepository
import com.togethertrip.main.trip.repository.TripExchangeRateRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

@Service
@Transactional(readOnly = true)
class TripExchangeRateService(
    private val tripRepository: TripRepository,
    private val tripCountryRepository: TripCountryRepository,
    private val tripExchangeRateRepository: TripExchangeRateRepository,
    private val userRepository: UserRepository,
    private val countryCurrencyResolver: CountryCurrencyResolver,
    private val exchangeRateClient: ExchangeRateClient,
) {

    fun getExchangeRates(
        userId: Long,
        tripId: Long,
    ): List<TripExchangeRateResponse> {
        getOwnedTrip(
            userId = userId,
            tripId = tripId,
        )

        return tripExchangeRateRepository
            .findByTripIdAndDeletedAtIsNullOrderByTargetCurrencyAsc(tripId)
            .map(TripExchangeRateResponse::from)
    }

    @Transactional
    fun refreshExchangeRates(
        userId: Long,
        tripId: Long,
    ): List<TripExchangeRateResponse> {
        val trip = getOwnedTrip(
            userId = userId,
            tripId = tripId,
        )
        val countries = tripCountryRepository.findByTripIdAndDeletedAtIsNullOrderBySortOrderAsc(trip.id)

        initializeExchangeRates(
            trip = trip,
            countries = countries,
        )

        return tripExchangeRateRepository
            .findByTripIdAndDeletedAtIsNullOrderByTargetCurrencyAsc(tripId)
            .map(TripExchangeRateResponse::from)
    }

    @Transactional
    fun updateExchangeRate(
        userId: Long,
        tripId: Long,
        exchangeRateId: Long,
        request: UpdateTripExchangeRateRequest,
    ): TripExchangeRateResponse {
        getOwnedTrip(
            userId = userId,
            tripId = tripId,
        )
        validateRate(request.rate)

        val exchangeRate = tripExchangeRateRepository.findByIdAndTripIdAndDeletedAtIsNull(
            id = exchangeRateId,
            tripId = tripId,
        ) ?: throw BusinessException(TripErrorCode.EXCHANGE_RATE_NOT_FOUND)

        exchangeRate.updateRate(
            rate = request.rate,
            source = SOURCE_MANUAL,
        )

        return TripExchangeRateResponse.from(exchangeRate)
    }

    @Transactional
    fun initializeExchangeRates(
        trip: Trip,
        countries: List<TripCountry>,
    ) {
        val rateDate = resolveRateDate(trip)
        val baseCurrency = normalizeCurrency(trip.defaultCurrency)
        val targetCurrencies = resolveTargetCurrencies(
            baseCurrency = baseCurrency,
            countries = countries,
        )
        val quotes = listOf(
            createBaseCurrencyQuote(
                baseCurrency = baseCurrency,
                rateDate = rateDate,
            )
        ) + exchangeRateClient.fetchRates(
            baseCurrency = baseCurrency,
            targetCurrencies = targetCurrencies,
            rateDate = rateDate,
        )

        upsertExchangeRates(
            trip = trip,
            baseCurrency = baseCurrency,
            targetCurrencies = targetCurrencies,
            rateDate = rateDate,
            quotes = quotes,
        )
    }

    fun resolveRateDate(trip: Trip): LocalDate {
        return trip.exchangeRateBaseDate
            ?: trip.startDate
            ?: LocalDate.ofInstant(trip.createdAt, ZoneOffset.UTC)
    }

    private fun upsertExchangeRates(
        trip: Trip,
        baseCurrency: String,
        targetCurrencies: Set<String>,
        rateDate: LocalDate,
        quotes: List<ExchangeRateQuote>,
    ) {
        val quotesByTargetCurrency = quotes.associateBy { quote ->
            normalizeCurrency(quote.targetCurrency)
        }

        targetCurrencies.forEach { targetCurrency ->
            val quote = quotesByTargetCurrency[targetCurrency]
                ?: throw BusinessException(TripErrorCode.EXCHANGE_RATE_FETCH_FAILED)
            validateRate(quote.rate)

            val existing = tripExchangeRateRepository.findByTripIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndDeletedAtIsNull(
                tripId = trip.id,
                baseCurrency = baseCurrency,
                targetCurrency = targetCurrency,
                rateDate = rateDate,
            )

            if (existing == null) {
                tripExchangeRateRepository.save(
                    TripExchangeRate(
                        trip = trip,
                        baseCurrency = baseCurrency,
                        targetCurrency = targetCurrency,
                        rate = quote.rate,
                        rateDate = rateDate,
                        source = quote.source,
                    )
                )
            } else {
                existing.updateRate(
                    rate = quote.rate,
                    source = quote.source,
                )
            }
        }
    }

    private fun resolveTargetCurrencies(
        baseCurrency: String,
        countries: List<TripCountry>,
    ): Set<String> {
        val countryCurrencies = countries
            .map { country -> countryCurrencyResolver.resolve(country.countryCode) }
            .map(::normalizeCurrency)

        return (countryCurrencies + baseCurrency).toSet()
    }

    private fun createBaseCurrencyQuote(
        baseCurrency: String,
        rateDate: LocalDate,
    ): ExchangeRateQuote {
        return ExchangeRateQuote(
            baseCurrency = baseCurrency,
            targetCurrency = baseCurrency,
            rate = BigDecimal("1.000000"),
            rateDate = rateDate,
            source = SOURCE_BASE_CURRENCY,
        )
    }

    private fun getOwnedTrip(
        userId: Long,
        tripId: Long,
    ): Trip {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        val trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
            ?: throw BusinessException(TripErrorCode.TRIP_NOT_FOUND)

        if (trip.ownerUser.id != userId) {
            throw BusinessException(TripErrorCode.TRIP_OWNER_ONLY)
        }

        return trip
    }

    private fun validateRate(rate: BigDecimal) {
        if (rate <= BigDecimal.ZERO) {
            throw BusinessException(TripErrorCode.INVALID_EXCHANGE_RATE)
        }
    }

    private fun normalizeCurrency(currency: String): String {
        return currency.trim().uppercase()
    }

    companion object {
        private const val SOURCE_BASE_CURRENCY = "BASE_CURRENCY"
        private const val SOURCE_MANUAL = "MANUAL"
    }
}
