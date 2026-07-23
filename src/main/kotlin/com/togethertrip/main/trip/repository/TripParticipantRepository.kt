package com.togethertrip.main.trip.repository

import com.togethertrip.main.settlement.domain.snapshot.SettlementParticipantRow
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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

    fun existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
        tripId: Long,
        userId: Long,
        participantStatus: TripParticipantStatus,
    ): Boolean

    fun findByTripIdAndDeletedAtIsNullOrderByCreatedAtAsc(tripId: Long): List<TripParticipant>

    fun countByTripIdAndParticipantStatusAndDeletedAtIsNull(
        tripId: Long,
        participantStatus: TripParticipantStatus,
    ): Int

    @Query(
        value = """
        select *
        from trip_participants
        where trip_id = :tripId
          and participant_status = :participantStatus
        order by created_at asc, id asc
        """,
        nativeQuery = true,
    )
    fun findByTripIdAndParticipantStatusIncludingDeleted(
        @Param("tripId") tripId: Long,
        @Param("participantStatus") participantStatus: String,
    ): List<TripParticipant>

    fun findByIdAndTripIdAndDeletedAtIsNull(
        id: Long,
        tripId: Long,
    ): TripParticipant?

    fun findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
        id: Long,
        tripId: Long,
        participantStatus: TripParticipantStatus,
    ): TripParticipant?

    @Query(
        value = """
        select participant.user_id
        from trip_participants participant
        join users user_account on user_account.id = participant.user_id
        where participant.trip_id = :tripId
          and participant.participant_status = :participantStatus
          and participant.deleted_at is null
          and participant.user_id is not null
          and user_account.status = :userStatus
          and user_account.deleted_at is null
        order by participant.created_at asc, participant.id asc
        """,
        nativeQuery = true,
    )
    fun findActiveUserIdsForNotification(
        @Param("tripId") tripId: Long,
        @Param("participantStatus") participantStatus: String,
        @Param("userStatus") userStatus: String,
    ): List<Long>

    @Query(
        value = """
        select participant.id as "participantId",
               participant.user_id as "userId",
               participant.display_name as "displayName",
               participant.profile_image_url as "profileImageUrl",
               participant.participant_status as "participantStatus",
               user_account.status as "userStatus"
        from trip_participants participant
        left join users user_account on user_account.id = participant.user_id
        where participant.trip_id = :tripId
          and participant.id in (:participantIds)
        order by participant.created_at asc, participant.id asc
        """,
        nativeQuery = true
    )
    fun findSettlementParticipantRows(
        @Param("tripId") tripId: Long,
        @Param("participantIds") participantIds: Collection<Long>,
    ): List<SettlementParticipantRow>
}
