package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.outbox.domain.OutboxAggregateType
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.common.DefaultOutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.trip.TripParticipantsAddedPayload
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.global.storage.ProfileImageUrlPolicy
import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import com.togethertrip.main.settlement.repository.projection.SettlementTransferCompletionSummary
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
import com.togethertrip.main.trip.dto.response.TripSettlementDisplayStatus
import com.togethertrip.main.trip.dto.response.TripSummaryResponse
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.pagination.TripCursor
import com.togethertrip.main.trip.repository.TripCountryRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.trip.service.support.TripNotificationRecipientResolver
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
    private val settlementTransferRepository: SettlementTransferRepository,
    private val userRepository: UserRepository,
    private val profileImageUrlPolicy: ProfileImageUrlPolicy,
    private val outboxEventPublisher: OutboxEventPublisher,
    private val tripNotificationRecipientResolver: TripNotificationRecipientResolver,
) {

    @Transactional
    fun createTrip(
        userId: Long,
        request: CreateTripRequest,
    ): TripDetailResponse {
        val user = getActiveUser(userId)
        validateTripDates(request.startDate, request.endDate)
        validateCompanionProfileImageUrls(request.participants)
        validateCompanionUserIds(user.id, request.participants)

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
        val companions = saveCompanions(trip, request.participants)
        publishParticipantsAdded(
            trip = trip,
            actor = user,
            participants = companions,
        )

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

        val displayStatusByTripId = findSettlementDisplayStatusByTripId(visibleTrips)
        val items = visibleTrips.map { trip ->
            TripSummaryResponse.from(
                trip = trip,
                settlementDisplayStatus = displayStatusByTripId[trip.id]
                    ?: resolveSettlementDisplayStatus(trip, null),
            )
        }
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
        val countries = updateTripCountryRows(
            trip = trip,
            existingCountries = existingCountries,
            requestedCountries = request.countries,
        )
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
            settlementDisplayStatus = resolveSettlementDisplayStatus(
                trip = trip,
                completionSummary = settlementTransferRepository.findCompletionSummaryByTripId(trip.id),
            ),
        )
    }

    private fun findSettlementDisplayStatusByTripId(
        trips: List<Trip>,
    ): Map<Long, TripSettlementDisplayStatus> {
        if (trips.isEmpty()) {
            return emptyMap()
        }

        val completionSummaryByTripId = settlementTransferRepository
            .findCompletionSummariesByTripIds(trips.map { it.id })
            .associateBy { it.tripId }

        return trips.associate { trip ->
            trip.id to resolveSettlementDisplayStatus(
                trip = trip,
                completionSummary = completionSummaryByTripId[trip.id],
            )
        }
    }

    private fun resolveSettlementDisplayStatus(
        trip: Trip,
        completionSummary: SettlementTransferCompletionSummary?,
    ): TripSettlementDisplayStatus {
        if (trip.settlementStatus == TripSettlementStatus.NOT_STARTED) {
            return TripSettlementDisplayStatus.NOT_STARTED
        }
        if (completionSummary != null && completionSummary.totalCount > 0) {
            return if (completionSummary.incompleteCount > 0) {
                TripSettlementDisplayStatus.IN_PROGRESS
            } else {
                TripSettlementDisplayStatus.COMPLETED
            }
        }

        return when (trip.settlementStatus) {
            TripSettlementStatus.NOT_STARTED -> TripSettlementDisplayStatus.NOT_STARTED
            TripSettlementStatus.IN_PROGRESS -> TripSettlementDisplayStatus.IN_PROGRESS
            TripSettlementStatus.SETTLED -> TripSettlementDisplayStatus.COMPLETED
        }
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

    private fun updateTripCountryRows(
        trip: Trip,
        existingCountries: List<TripCountry>,
        requestedCountries: List<TripCountryInput>,
    ): List<TripCountry> {
        val normalizedCountries = requestedCountries
            .mapIndexed { index, country ->
                NormalizedTripCountryInput(
                    countryCode = country.countryCode.trim().uppercase(),
                    countryName = country.countryName.trim(),
                    sortOrder = country.sortOrder ?: index,
                )
            }
            .distinctBy { it.countryCode }

        val existingByCode = existingCountries.associateBy { it.countryCode.uppercase() }
        val requestedCodes = normalizedCountries.mapTo(mutableSetOf()) { it.countryCode }

        existingCountries
            .filter { it.countryCode.uppercase() !in requestedCodes }
            .forEach { it.markDeleted() }

        return normalizedCountries.map { country ->
            val existingCountry = existingByCode[country.countryCode]
            if (existingCountry != null) {
                existingCountry.countryName = country.countryName
                existingCountry.sortOrder = country.sortOrder
                existingCountry.deletedAt = null
                existingCountry.updatedAt = Instant.now()
                existingCountry
            } else {
                tripCountryRepository.save(
                    TripCountry(
                        trip = trip,
                        countryCode = country.countryCode,
                        countryName = country.countryName,
                        sortOrder = country.sortOrder,
                    )
                )
            }
        }
    }

    private data class NormalizedTripCountryInput(
        val countryCode: String,
        val countryName: String,
        val sortOrder: Int,
    )

    private fun saveCompanions(
        trip: Trip,
        participants: List<TripCompanionInput>,
    ): List<TripParticipant> {
        return participants.map { participant ->
            val user = participant.userId?.let(::getActiveUser)
            tripParticipantRepository.save(
                TripParticipant(
                    trip = trip,
                    user = user,
                    displayName = user?.nickname ?: participant.displayName.trim(),
                    profileImageUrl = user?.profileImageUrl
                        ?: resolveCompanionProfileImageUrl(participant.profileImageUrl),
                    participantRole = TripParticipantRole.MEMBER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                    joinedAt = user?.let { Instant.now() },
                )
            )
        }
    }

    private fun publishParticipantsAdded(
        trip: Trip,
        actor: User,
        participants: List<TripParticipant>,
    ) {
        val userParticipants = participants.filter { participant -> participant.user != null }
        val recipients = userParticipants
            .mapNotNull { participant ->
                tripNotificationRecipientResolver.resolveSingleUserId(
                    userId = participant.user?.id,
                    actorUserId = actor.id,
                )
            }
            .map(::DefaultOutboxRecipientPayload)

        outboxEventPublisher.publish(
            aggregateType = OutboxAggregateType.TRIP,
            aggregateId = trip.id,
            eventType = OutboxEventType.TRIP_PARTICIPANTS_ADDED,
            payload = TripParticipantsAddedPayload(
                recipients = recipients,
                actorUserId = actor.id,
                tripId = trip.id,
                participantIds = userParticipants.map { participant -> participant.id },
                tripName = trip.title,
                actorDisplayName = actor.nickname,
                occurredAt = Instant.now(),
            ),
        )
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

    private fun validateCompanionProfileImageUrls(participants: List<TripCompanionInput>) {
        participants.forEach { participant ->
            resolveCompanionProfileImageUrl(participant.profileImageUrl)
        }
    }

    private fun validateCompanionUserIds(
        ownerUserId: Long,
        participants: List<TripCompanionInput>,
    ) {
        val userIds = participants.mapNotNull { participant -> participant.userId }
        if (userIds.any { userId -> userId == ownerUserId }) {
            throw BusinessException(TripErrorCode.TRIP_ALREADY_JOINED)
        }
        if (userIds.toSet().size != userIds.size) {
            throw BusinessException(TripErrorCode.TRIP_ALREADY_JOINED)
        }
    }

    private fun resolveCompanionProfileImageUrl(profileImageUrl: String?): String? {
        val trimmedProfileImageUrl = profileImageUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        if (!profileImageUrlPolicy.isAllowed(trimmedProfileImageUrl)) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        return trimmedProfileImageUrl
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
