package com.togethertrip.main.trip.service.support

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Component

@Component
class TripAccessResolver(
    private val tripRepository: TripRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val userRepository: UserRepository,
) {

    fun getActiveUser(userId: Long): User {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        return user
    }

    fun getAccessibleTrip(
        userId: Long,
        tripId: Long,
    ): Trip {
        val trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
            ?: throw BusinessException(TripErrorCode.TRIP_NOT_FOUND)

        return assertAccessibleTrip(userId, tripId, trip)
    }

    fun getAccessibleTripForUpdate(
        userId: Long,
        tripId: Long,
    ): Trip {
        val trip = tripRepository.findByIdAndDeletedAtIsNullForUpdate(tripId)
            ?: throw BusinessException(TripErrorCode.TRIP_NOT_FOUND)

        return assertAccessibleTrip(userId, tripId, trip)
    }

    private fun assertAccessibleTrip(
        userId: Long,
        tripId: Long,
        trip: Trip,
    ): Trip {

        if (trip.ownerUser.id == userId) {
            return trip
        }

        val participant = tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            tripId = tripId,
            userId = userId,
            participantStatus = TripParticipantStatus.ACTIVE,
        ) ?: throw BusinessException(TripErrorCode.TRIP_ACCESS_DENIED)

        return participant.trip
    }

    fun getActiveParticipantById(
        tripId: Long,
        participantId: Long,
    ): TripParticipant {
        return tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
            id = participantId,
            tripId = tripId,
            participantStatus = TripParticipantStatus.ACTIVE,
        ) ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)
    }

    fun getActiveParticipantByUserId(
        tripId: Long,
        userId: Long,
    ): TripParticipant {
        return tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            tripId = tripId,
            userId = userId,
            participantStatus = TripParticipantStatus.ACTIVE,
        ) ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)
    }
}
