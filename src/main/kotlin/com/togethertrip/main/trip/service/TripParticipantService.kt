package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.dto.request.AddTripParticipantRequest
import com.togethertrip.main.trip.dto.request.LinkTripParticipantRequest
import com.togethertrip.main.trip.dto.request.UpdateTripParticipantRequest
import com.togethertrip.main.trip.dto.response.TripParticipantSummaryResponse
import com.togethertrip.main.trip.dto.response.TripParticipantType
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
@Transactional(readOnly = true)
class TripParticipantService(
    private val tripRepository: TripRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    @Transactional
    fun addTemporaryParticipant(
        userId: Long,
        tripId: Long,
        request: AddTripParticipantRequest,
    ): TripParticipantSummaryResponse {
        getActiveUser(userId)
        val trip = getOwnedTrip(userId, tripId)
        validateParticipantWritable(trip)

        val participant = tripParticipantRepository.save(
            TripParticipant(
                trip = trip,
                displayName = normalizeRequiredName(request.displayName),
                profileImageUrl = normalizeOptional(request.profileImageUrl),
                participantRole = TripParticipantRole.MEMBER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        )

        return TripParticipantSummaryResponse.from(participant)
    }

    fun getParticipants(
        userId: Long,
        tripId: Long,
        status: String?,
        type: String?,
    ): List<TripParticipantSummaryResponse> {
        getActiveUser(userId)
        getAccessibleTrip(userId, tripId)

        val participantStatus = status?.let(::parseParticipantStatus)
        val participantType = type?.let(::parseParticipantType)

        val participants = if (participantStatus == null) {
            tripParticipantRepository.findByTripIdAndDeletedAtIsNullOrderByCreatedAtAsc(tripId)
        } else {
            tripParticipantRepository.findByTripIdAndParticipantStatusIncludingDeleted(
                tripId = tripId,
                participantStatus = participantStatus.name,
            )
        }

        return participants
            .asSequence()
            .filter { participantType == null || resolveParticipantType(it) == participantType }
            .map(TripParticipantSummaryResponse::from)
            .toList()
    }

    fun getParticipant(
        userId: Long,
        tripId: Long,
        participantId: Long,
    ): TripParticipantSummaryResponse {
        getActiveUser(userId)
        getAccessibleTrip(userId, tripId)

        val participant = getParticipantInTrip(
            tripId = tripId,
            participantId = participantId,
        )

        return TripParticipantSummaryResponse.from(participant)
    }

    @Transactional
    fun updateParticipant(
        userId: Long,
        tripId: Long,
        participantId: Long,
        request: UpdateTripParticipantRequest,
    ): TripParticipantSummaryResponse {
        getActiveUser(userId)
        getOwnedTrip(userId, tripId)
        validateUpdateRequest(request)

        val participant = getParticipantInTrip(
            tripId = tripId,
            participantId = participantId,
        )

        request.displayName?.let {
            participant.displayName = normalizeRequiredName(it)
        }
        request.profileImageUrl?.let {
            participant.profileImageUrl = normalizeOptional(it)
        }
        participant.updatedAt = Instant.now(clock)

        return TripParticipantSummaryResponse.from(participant)
    }

    @Transactional
    fun removeParticipant(
        userId: Long,
        tripId: Long,
        participantId: Long,
    ) {
        getActiveUser(userId)
        val trip = getOwnedTrip(userId, tripId)

        val participant = getParticipantInTrip(
            tripId = tripId,
            participantId = participantId,
        )
        if (
            participant.participantRole == TripParticipantRole.LEADER ||
            participant.user?.id == trip.ownerUser.id
        ) {
            throw BusinessException(TripErrorCode.TRIP_LEADER_REMOVE_DENIED)
        }

        val now = Instant.now(clock)
        participant.participantStatus = TripParticipantStatus.REMOVED
        participant.leftAt = now
        participant.markDeleted(now)
    }

    @Transactional
    fun linkTemporaryParticipant(
        userId: Long,
        tripId: Long,
        request: LinkTripParticipantRequest,
    ): TripParticipantSummaryResponse {
        getActiveUser(userId)
        val trip = getOwnedTrip(userId, tripId)
        validateParticipantWritable(trip)

        val user = getActiveUser(request.userId)
        if (
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = tripId,
                userId = user.id,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ) {
            throw BusinessException(TripErrorCode.TRIP_ALREADY_JOINED)
        }

        val participant = tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
            id = request.participantId,
            tripId = tripId,
            participantStatus = TripParticipantStatus.ACTIVE,
        ) ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)

        if (participant.user != null) {
            throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_ALREADY_LINKED)
        }

        val now = Instant.now(clock)
        participant.user = user
        participant.displayName = user.nickname
        participant.profileImageUrl = user.profileImageUrl
        participant.joinedAt = now
        participant.updatedAt = now

        val savedParticipant = try {
            tripParticipantRepository.saveAndFlush(participant)
        } catch (_: DataIntegrityViolationException) {
            throw BusinessException(TripErrorCode.TRIP_ALREADY_JOINED)
        }

        return TripParticipantSummaryResponse.from(savedParticipant)
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

        val participant = tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            tripId = tripId,
            userId = userId,
            participantStatus = TripParticipantStatus.ACTIVE,
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

    private fun getParticipantInTrip(
        tripId: Long,
        participantId: Long,
    ): TripParticipant {
        return tripParticipantRepository.findByIdAndTripIdAndDeletedAtIsNull(
            id = participantId,
            tripId = tripId,
        ) ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)
    }

    private fun getActiveUser(userId: Long): User {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        return user
    }

    private fun validateParticipantWritable(trip: Trip) {
        if (trip.settlementStatus != TripSettlementStatus.NOT_STARTED) {
            throw BusinessException(TripErrorCode.TRIP_JOIN_CLOSED)
        }
    }

    private fun validateUpdateRequest(request: UpdateTripParticipantRequest) {
        if (request.displayName == null && request.profileImageUrl == null) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun parseParticipantStatus(status: String): TripParticipantStatus {
        return try {
            TripParticipantStatus.valueOf(status.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw BusinessException(TripErrorCode.INVALID_TRIP_PARTICIPANT_STATUS)
        }
    }

    private fun parseParticipantType(type: String): TripParticipantType {
        return try {
            TripParticipantType.valueOf(type.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw BusinessException(TripErrorCode.INVALID_TRIP_PARTICIPANT_TYPE)
        }
    }

    private fun resolveParticipantType(participant: TripParticipant): TripParticipantType {
        return if (participant.user == null) {
            TripParticipantType.TEMPORARY
        } else {
            TripParticipantType.USER
        }
    }

    private fun normalizeRequiredName(value: String): String {
        return value.trim().takeIf { it.isNotBlank() }
            ?: throw BusinessException(CommonErrorCode.INVALID_INPUT)
    }

    private fun normalizeOptional(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotBlank() }
    }
}
