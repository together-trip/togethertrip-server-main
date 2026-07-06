package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.outbox.domain.OutboxAggregateType
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.common.DefaultOutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.trip.TripParticipantRemovedPayload
import com.togethertrip.main.global.outbox.payload.trip.TripParticipantsAddedPayload
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
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
import com.togethertrip.main.trip.service.support.TripNotificationRecipientResolver
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
    private val outboxEventPublisher: OutboxEventPublisher,
    private val tripNotificationRecipientResolver: TripNotificationRecipientResolver,
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
        val trip = getAccessibleTrip(userId, tripId)

        val participantStatus = status?.let(::parseParticipantStatus)
        val participantType = type?.let(::parseParticipantType)

        val participants = if (participantStatus == null) {
            tripParticipantRepository.findByTripIdAndDeletedAtIsNullOrderByCreatedAtAsc(tripId)
        } else {
            validateTripOwner(userId, trip)
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
        val trip = getOwnedTrip(userId, tripId)
        validateParticipantWritable(trip)
        validateUpdateRequest(request)

        val participant = getActiveParticipantInTrip(
            tripId = tripId,
            participantId = participantId,
        )
        participant.updateTemporaryProfile(
            displayName = request.displayName?.let(::normalizeRequiredName),
            profileImageUrl = normalizeOptional(request.profileImageUrl),
            profileImageUrlChanged = request.profileImageUrl != null,
            updatedAt = Instant.now(clock),
        )

        return TripParticipantSummaryResponse.from(participant)
    }

    @Transactional
    fun removeParticipant(
        userId: Long,
        tripId: Long,
        participantId: Long,
    ) {
        val actor = getActiveUser(userId)
        val trip = getOwnedTrip(userId, tripId)
        validateParticipantWritable(trip)

        val participant = getActiveParticipantInTrip(
            tripId = tripId,
            participantId = participantId,
        )
        val now = Instant.now(clock)
        participant.remove(
            tripOwnerUserId = trip.ownerUser.id,
            removedAt = now,
        )
        publishParticipantRemoved(
            trip = trip,
            actor = actor,
            participant = participant,
            occurredAt = now,
        )
    }

    @Transactional
    fun linkTemporaryParticipant(
        userId: Long,
        tripId: Long,
        request: LinkTripParticipantRequest,
    ): TripParticipantSummaryResponse {
        val actor = getActiveUser(userId)
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

        val now = Instant.now(clock)
        participant.linkUser(
            user = user,
            linkedAt = now,
        )

        val savedParticipant = try {
            tripParticipantRepository.saveAndFlush(participant)
        } catch (_: DataIntegrityViolationException) {
            throw BusinessException(TripErrorCode.TRIP_ALREADY_JOINED)
        }
        publishParticipantsAdded(
            trip = trip,
            actor = actor,
            participant = savedParticipant,
            occurredAt = now,
        )

        return TripParticipantSummaryResponse.from(savedParticipant)
    }

    private fun publishParticipantsAdded(
        trip: Trip,
        actor: User,
        participant: TripParticipant,
        occurredAt: Instant,
    ) {
        val recipientUserId = tripNotificationRecipientResolver.resolveSingleUserId(
            userId = participant.user?.id,
            actorUserId = actor.id,
        )
        val recipients = recipientUserId
            ?.let { userId -> listOf(DefaultOutboxRecipientPayload(userId)) }
            ?: emptyList()

        outboxEventPublisher.publish(
            aggregateType = OutboxAggregateType.TRIP,
            aggregateId = trip.id,
            eventType = OutboxEventType.TRIP_PARTICIPANTS_ADDED,
            payload = TripParticipantsAddedPayload(
                recipients = recipients,
                actorUserId = actor.id,
                tripId = trip.id,
                participantIds = listOf(participant.id),
                tripName = trip.title,
                actorDisplayName = actor.nickname,
                occurredAt = occurredAt,
            ),
        )
    }

    private fun publishParticipantRemoved(
        trip: Trip,
        actor: User,
        participant: TripParticipant,
        occurredAt: Instant,
    ) {
        val recipientUserId = tripNotificationRecipientResolver.resolveSingleUserId(
            userId = participant.user?.id,
            actorUserId = actor.id,
        )
        val recipients = recipientUserId
            ?.let { userId -> listOf(DefaultOutboxRecipientPayload(userId)) }
            ?: emptyList()

        outboxEventPublisher.publish(
            aggregateType = OutboxAggregateType.TRIP,
            aggregateId = trip.id,
            eventType = OutboxEventType.TRIP_PARTICIPANT_REMOVED,
            payload = TripParticipantRemovedPayload(
                recipients = recipients,
                actorUserId = actor.id,
                tripId = trip.id,
                participantId = participant.id,
                tripName = trip.title,
                actorDisplayName = actor.nickname,
                occurredAt = occurredAt,
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
        validateTripOwner(userId, trip)

        return trip
    }

    private fun validateTripOwner(
        userId: Long,
        trip: Trip,
    ) {
        if (trip.ownerUser.id != userId) {
            throw BusinessException(TripErrorCode.TRIP_OWNER_ONLY)
        }
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

    private fun getActiveParticipantInTrip(
        tripId: Long,
        participantId: Long,
    ): TripParticipant {
        return tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
            id = participantId,
            tripId = tripId,
            participantStatus = TripParticipantStatus.ACTIVE,
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
