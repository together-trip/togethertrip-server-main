package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantStatus
import org.springframework.data.jpa.repository.JpaRepository

interface TripParticipantRepository : JpaRepository<TripParticipant, Long> {
    fun findByTripIdAndUserIdAndDeletedAtIsNull(
        tripId: Long,
        userId: Long,
    ): TripParticipant?

    fun findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
        tripId: Long,
        userId: Long,
        participantStatus: TripParticipantStatus,
    ): TripParticipant?

    fun findByTripIdAndDeletedAtIsNullOrderByCreatedAtAsc(tripId: Long): List<TripParticipant>

    fun findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
        id: Long,
        tripId: Long,
        participantStatus: TripParticipantStatus,
    ): TripParticipant?
}
