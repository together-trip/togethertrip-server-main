package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripCountry
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.domain.TripStatus
import com.togethertrip.main.trip.dto.request.CreateTripRequest
import com.togethertrip.main.trip.dto.request.TripCompanionInput
import com.togethertrip.main.trip.dto.request.TripCountryInput
import com.togethertrip.main.trip.dto.request.UpdateTripCountriesRequest
import com.togethertrip.main.trip.dto.request.UpdateTripRequest
import com.togethertrip.main.trip.dto.response.TripCountriesResponse
import com.togethertrip.main.trip.dto.response.TripCountryResponse
import com.togethertrip.main.trip.dto.response.TripDetailResponse
import com.togethertrip.main.trip.dto.response.TripListResponse
import com.togethertrip.main.trip.dto.response.TripParticipantSummaryResponse
import com.togethertrip.main.trip.dto.response.TripSummaryResponse
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.pagination.TripCursor
import com.togethertrip.main.trip.repository.TripCountryRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class TripService(
    private val tripRepository: TripRepository,
    private val tripCountryRepository: TripCountryRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun createTrip(
        userId: Long,
        request: CreateTripRequest,
    ): TripDetailResponse {
        val user = getActiveUser(userId)
        validateTripDates(request.startDate, request.endDate)

        val trip = tripRepository.save(
            Trip(
                ownerUser = user,
                title = request.title.trim(),
                defaultCurrency = request.defaultCurrency.trim().uppercase(),
                exchangeRateBaseDate = request.exchangeRateBaseDate,
                startDate = request.startDate,
                endDate = request.endDate,
                tripStatus = resolveInitialTripStatus(
                    startDate = request.startDate,
                    endDate = request.endDate,
                ),
                settlementStatus = TripSettlementStatus.NOT_STARTED,
            )
        )

        tripParticipantRepository.save(
            TripParticipant(
                trip = trip,
                user = user,
                displayName = user.nickname,
                profileImageUrl = user.profileImageUrl,
                participantRole = TripParticipantRole.LEADER,
                participantStatus = TripParticipantStatus.ACTIVE,
                joinedAt = Instant.now(),
            )
        )

        saveTripCountries(trip, request.countries)
        saveCompanions(trip, request.participants)

        return buildTripDetailResponse(trip)
    }

    fun getTrips(
        userId: Long,
        status: String?,
        cursor: String?,
        size: Int?,
    ): TripListResponse {
        getActiveUser(userId)

        val requestedSize = (size ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        val pageable = PageRequest.of(0, requestedSize + 1)
        val tripStatus = status?.let(::parseTripStatus)
        val tripCursor = cursor?.let(::parseTripCursor)
        val trips = if (tripCursor == null) {
            tripRepository.findAccessibleTrips(
                userId = userId,
                status = tripStatus,
                pageable = pageable,
            )
        } else {
            tripRepository.findAccessibleTripsAfterCursor(
                userId = userId,
                status = tripStatus,
                cursorCreatedAt = tripCursor.createdAt,
                cursorId = tripCursor.id,
                pageable = pageable,
            )
        }
        val hasNext = trips.size > requestedSize
        val visibleTrips = if (hasNext) trips.take(requestedSize) else trips

        val items = visibleTrips.map(TripSummaryResponse::from)
        val nextCursor = visibleTrips
            .lastOrNull()
            ?.takeIf { hasNext }
            ?.let(::createNextCursor)

        return TripListResponse.from(
            items = items,
            size = requestedSize,
            nextCursor = nextCursor,
            hasNext = hasNext,
        )
    }

    fun getTrip(
        userId: Long,
        tripId: Long,
    ): TripDetailResponse {
        getActiveUser(userId)
        val trip = getAccessibleTrip(userId, tripId)

        return buildTripDetailResponse(trip)
    }

    @Transactional
    fun updateTrip(
        userId: Long,
        tripId: Long,
        request: UpdateTripRequest,
    ): TripDetailResponse {
        getActiveUser(userId)
        val trip = getOwnedTrip(userId, tripId)

        validateTripDates(request.startDate, request.endDate)
        validateUpdateRequest(request)

        trip.updateBasicInfo(
            title = request.title?.trim(),
            defaultCurrency = request.defaultCurrency?.trim()?.uppercase(),
            exchangeRateBaseDate = request.exchangeRateBaseDate,
            startDate = request.startDate,
            endDate = request.endDate,
        )
        trip.tripStatus = resolveInitialTripStatus(
            startDate = trip.startDate,
            endDate = trip.endDate,
        )

        return buildTripDetailResponse(trip)
    }

    @Transactional
    fun deleteTrip(
        userId: Long,
        tripId: Long,
    ) {
        getActiveUser(userId)
        val trip = getOwnedTrip(userId, tripId)
        trip.markDeleted()
    }

    @Transactional
    fun updateTripCountries(
        userId: Long,
        tripId: Long,
        request: UpdateTripCountriesRequest,
    ): TripCountriesResponse {
        getActiveUser(userId)
        val trip = getOwnedTrip(userId, tripId)
        val existingCountries = tripCountryRepository.findByTripIdAndDeletedAtIsNullOrderBySortOrderAsc(trip.id)

        existingCountries.forEach { it.markDeleted() }

        val countries = saveTripCountries(trip, request.countries)
            .map(TripCountryResponse::from)

        return TripCountriesResponse.from(
            trip = trip,
            countries = countries,
        )
    }

    private fun buildTripDetailResponse(trip: Trip): TripDetailResponse {
        val countries = tripCountryRepository.findByTripIdAndDeletedAtIsNullOrderBySortOrderAsc(trip.id)
            .map(TripCountryResponse::from)
        val participants = tripParticipantRepository.findByTripIdAndDeletedAtIsNullOrderByCreatedAtAsc(trip.id)
            .map(TripParticipantSummaryResponse::from)

        return TripDetailResponse.from(
            trip = trip,
            countries = countries,
            participants = participants,
        )
    }

    private fun saveTripCountries(
        trip: Trip,
        countries: List<TripCountryInput>,
    ): List<TripCountry> {
        return countries.mapIndexed { index, country ->
            tripCountryRepository.save(
                TripCountry(
                    trip = trip,
                    countryCode = country.countryCode.trim().uppercase(),
                    countryName = country.countryName.trim(),
                    sortOrder = country.sortOrder ?: index,
                )
            )
        }
    }

    private fun saveCompanions(
        trip: Trip,
        participants: List<TripCompanionInput>,
    ): List<TripParticipant> {
        return participants.map { participant ->
            tripParticipantRepository.save(
                TripParticipant(
                    trip = trip,
                    displayName = participant.displayName.trim(),
                    profileImageUrl = participant.profileImageUrl?.trim(),
                    participantRole = TripParticipantRole.MEMBER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                )
            )
        }
    }

    private fun getAccessibleTrip(
        userId: Long,
        tripId: Long,
    ): Trip {
        val trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
            ?: throw BusinessException(TripErrorCode.TRIP_NOT_FOUND)

        if (trip.ownerUser.id == userId) {
            return trip
        }

        val participant = tripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(
            tripId = tripId,
            userId = userId,
        )

        if (participant == null) {
            throw BusinessException(TripErrorCode.TRIP_ACCESS_DENIED)
        }

        return trip
    }

    private fun getOwnedTrip(
        userId: Long,
        tripId: Long,
    ): Trip {
        val trip = getAccessibleTrip(userId, tripId)

        if (trip.ownerUser.id != userId) {
            throw BusinessException(TripErrorCode.TRIP_OWNER_ONLY)
        }

        return trip
    }

    private fun getActiveUser(userId: Long): User {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        return user
    }

    private fun parseTripStatus(status: String): TripStatus {
        return try {
            TripStatus.valueOf(status.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw BusinessException(TripErrorCode.INVALID_TRIP_STATUS)
        }
    }

    private fun parseTripCursor(cursor: String): TripCursor {
        return try {
            TripCursor.decode(cursor)
        } catch (_: RuntimeException) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun createNextCursor(trip: Trip): String {
        return TripCursor(
            createdAt = trip.createdAt,
            id = trip.id,
        ).encode()
    }

    private fun validateTripDates(
        startDate: LocalDate?,
        endDate: LocalDate?,
    ) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun validateUpdateRequest(request: UpdateTripRequest) {
        if (
            request.title == null &&
            request.defaultCurrency == null &&
            request.exchangeRateBaseDate == null &&
            request.startDate == null &&
            request.endDate == null
        ) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun resolveInitialTripStatus(
        startDate: LocalDate?,
        endDate: LocalDate?,
        today: LocalDate = LocalDate.now(),
    ): TripStatus {
        return when {
            startDate != null && startDate.isAfter(today) -> TripStatus.PLANNED
            endDate != null && endDate.isBefore(today) -> TripStatus.COMPLETED
            startDate != null && endDate != null -> TripStatus.ONGOING
            else -> TripStatus.PLANNED
        }
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 100
    }
}
