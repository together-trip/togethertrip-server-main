package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.trip.client.ExchangeRateClient
import com.togethertrip.main.trip.client.ExchangeRateQuote
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripCountry
import com.togethertrip.main.trip.domain.TripExchangeRate
import com.togethertrip.main.trip.dto.request.UpdateTripExchangeRateRequest
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripCountryRepository
import com.togethertrip.main.trip.repository.TripExchangeRateRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TripExchangeRateServiceTest {

    private lateinit var tripRepository: TripRepository
    private lateinit var tripCountryRepository: TripCountryRepository
    private lateinit var tripExchangeRateRepository: TripExchangeRateRepository
    private lateinit var userRepository: UserRepository
    private lateinit var countryCurrencyResolver: CountryCurrencyResolver
    private lateinit var exchangeRateClient: ExchangeRateClient
    private lateinit var tripExchangeRateService: TripExchangeRateService

    @BeforeEach
    fun setUp() {
        tripRepository = mock(TripRepository::class.java)
        tripCountryRepository = mock(TripCountryRepository::class.java)
        tripExchangeRateRepository = mock(TripExchangeRateRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        countryCurrencyResolver = CountryCurrencyResolver()
        exchangeRateClient = mock(ExchangeRateClient::class.java)
        tripExchangeRateService = TripExchangeRateService(
            tripRepository = tripRepository,
            tripCountryRepository = tripCountryRepository,
            tripExchangeRateRepository = tripExchangeRateRepository,
            userRepository = userRepository,
            countryCurrencyResolver = countryCurrencyResolver,
            exchangeRateClient = exchangeRateClient,
        )
    }

    @Test
    fun `여행 환율 초기화 시 기본 통화와 국가 통화를 저장한다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        val countries = listOf(
            createCountry(
                trip = trip,
                countryCode = "KR",
                countryName = "대한민국",
                sortOrder = 0,
            ),
            createCountry(
                trip = trip,
                countryCode = "JP",
                countryName = "일본",
                sortOrder = 1,
            ),
        )
        val savedRates = mutableListOf<TripExchangeRate>()

        `when`(
            exchangeRateClient.fetchRates(
                baseCurrency = "KRW",
                targetCurrencies = setOf("KRW", "JPY"),
                rateDate = LocalDate.of(2026, 7, 1),
            )
        ).thenReturn(
            listOf(
                ExchangeRateQuote(
                    baseCurrency = "KRW",
                    targetCurrency = "JPY",
                    rate = BigDecimal("9.150000"),
                    rateDate = LocalDate.of(2026, 7, 1),
                    source = "TEST",
                )
            )
        )
        `when`(
            tripExchangeRateRepository.save(org.mockito.ArgumentMatchers.any(TripExchangeRate::class.java))
        ).thenAnswer { invocation ->
            (invocation.arguments[0] as TripExchangeRate).apply {
                id = (100L + savedRates.size)
                savedRates.add(this)
            }
        }

        tripExchangeRateService.initializeExchangeRates(
            trip = trip,
            countries = countries,
        )

        assertEquals(2, savedRates.size)
        assertEquals("KRW", savedRates[0].targetCurrency)
        assertEquals(BigDecimal("1.000000"), savedRates[0].rate)
        assertEquals("BASE_CURRENCY", savedRates[0].source)
        assertEquals(LocalDate.of(2026, 7, 1), savedRates[0].rateDate)
        assertEquals("JPY", savedRates[1].targetCurrency)
        assertEquals(BigDecimal("9.150000"), savedRates[1].rate)
        assertEquals("TEST", savedRates[1].source)
    }

    @Test
    fun `환율 기준일은 exchangeRateBaseDate가 우선한다`() {
        val trip = createTrip(createUser()).apply {
            exchangeRateBaseDate = LocalDate.of(2026, 6, 15)
            startDate = LocalDate.of(2026, 7, 1)
        }

        val rateDate = tripExchangeRateService.resolveRateDate(trip)

        assertEquals(LocalDate.of(2026, 6, 15), rateDate)
    }

    @Test
    fun `환율 초기화 시 현재 국가 통화가 아니면 기존 환율을 soft delete 한다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        val countries = listOf(
            createCountry(
                trip = trip,
                countryCode = "VN",
                countryName = "베트남",
                sortOrder = 0,
            )
        )
        val obsoleteRate = TripExchangeRate(
            trip = trip,
            baseCurrency = "KRW",
            targetCurrency = "JPY",
            rate = BigDecimal("9.150000"),
            rateDate = LocalDate.of(2026, 7, 1),
            source = "TEST",
        )

        `when`(
            tripExchangeRateRepository.findByTripIdAndBaseCurrencyAndRateDateAndDeletedAtIsNull(
                tripId = 10L,
                baseCurrency = "KRW",
                rateDate = LocalDate.of(2026, 7, 1),
            )
        ).thenReturn(listOf(obsoleteRate))
        `when`(
            exchangeRateClient.fetchRates(
                baseCurrency = "KRW",
                targetCurrencies = setOf("KRW", "VND"),
                rateDate = LocalDate.of(2026, 7, 1),
            )
        ).thenReturn(
            listOf(
                ExchangeRateQuote(
                    baseCurrency = "KRW",
                    targetCurrency = "VND",
                    rate = BigDecimal("0.054000"),
                    rateDate = LocalDate.of(2026, 7, 1),
                    source = "TEST",
                )
            )
        )

        tripExchangeRateService.initializeExchangeRates(
            trip = trip,
            countries = countries,
        )

        kotlin.test.assertNotNull(obsoleteRate.deletedAt)
    }

    @Test
    fun `현재 여행 환율 기준이 아닌 환율은 수정할 수 없다`() {
        val owner = createUser()
        val trip = createTrip(owner)

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(
            tripExchangeRateRepository.findByIdAndTripIdAndBaseCurrencyAndRateDateAndDeletedAtIsNull(
                id = 100L,
                tripId = 10L,
                baseCurrency = "KRW",
                rateDate = LocalDate.of(2026, 7, 1),
            )
        ).thenReturn(null)

        val exception = assertFailsWith<BusinessException> {
            tripExchangeRateService.updateExchangeRate(
                userId = 1L,
                tripId = 10L,
                exchangeRateId = 100L,
                request = UpdateTripExchangeRateRequest(rate = BigDecimal("9.500000")),
            )
        }

        assertEquals(TripErrorCode.EXCHANGE_RATE_NOT_FOUND, exception.errorCode)
    }

    private fun createUser(): User {
        return User(
            nickname = "재완",
            profileImageUrl = null,
        ).apply {
            id = 1L
        }
    }

    private fun createTrip(owner: User): Trip {
        return Trip(
            ownerUser = owner,
            title = "일본 여행",
            defaultCurrency = "KRW",
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 5),
        ).apply {
            id = 10L
            createdAt = Instant.parse("2026-06-01T00:00:00Z")
        }
    }

    private fun createCountry(
        trip: Trip,
        countryCode: String,
        countryName: String,
        sortOrder: Int,
    ): TripCountry {
        return TripCountry(
            trip = trip,
            countryCode = countryCode,
            countryName = countryName,
            sortOrder = sortOrder,
        )
    }
}
