package com.togethertrip.main.settlement.service.support

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
import org.springframework.transaction.annotation.Transactional

@Component
class SettlementAccessResolver(
    private val tripRepository: TripRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun getAccessibleTrip(
        userId: Long,
        tripId: Long,
    ): Trip {
        getActiveUser(userId)
        val trip = getTripOrThrow(tripId)
        val participant = getActiveParticipantOrNull(
            userId = userId,
            tripId = tripId,
        )

        if (participant == null) {
            throw BusinessException(TripErrorCode.TRIP_ACCESS_DENIED)
        }

        return trip
    }

    @Transactional(readOnly = true)
    fun getOwnedTrip(
        userId: Long,
        tripId: Long,
    ): Trip {
        val trip = getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )

        if (trip.ownerUser.id != userId) {
            throw BusinessException(TripErrorCode.TRIP_OWNER_ONLY)
        }

        return trip
    }

    @Transactional(readOnly = true)
    fun getActiveParticipant(
        userId: Long,
        tripId: Long,
    ): TripParticipant {
        getActiveUser(userId)
        getTripOrThrow(tripId)

        return getActiveParticipantOrNull(
            userId = userId,
            tripId = tripId,
        ) ?: throw BusinessException(TripErrorCode.TRIP_ACCESS_DENIED)
    }

    @Transactional(readOnly = true)
    fun getActiveUser(userId: Long): User {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        return user
    }

    private fun getTripOrThrow(tripId: Long): Trip {
        return tripRepository.findByIdAndDeletedAtIsNull(tripId)
            ?: throw BusinessException(TripErrorCode.TRIP_NOT_FOUND)
    }

    private fun getActiveParticipantOrNull(
        userId: Long,
        tripId: Long,
    ): TripParticipant? {
        return tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            tripId = tripId,
            userId = userId,
            participantStatus = TripParticipantStatus.ACTIVE,
        )
    }
}
