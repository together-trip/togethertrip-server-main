package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface TripRepository : JpaRepository<Trip, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Trip?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id and t.deletedAt is null")
    fun findByIdAndDeletedAtIsNullForUpdate(@Param("id") id: Long): Trip?

    @Query(
        """
        select distinct t
        from Trip t
        left join TripParticipant tp on tp.trip = t and tp.deletedAt is null
        where t.deletedAt is null
          and (t.ownerUser.id = :userId or tp.user.id = :userId)
          and (:status is null or t.tripStatus = :status)
        order by t.createdAt desc, t.id desc
        """
    )
    fun findAccessibleTrips(
        @Param("userId") userId: Long,
        @Param("status") status: TripStatus?,
        pageable: Pageable,
    ): List<Trip>

    @Query(
        """
        select distinct t
        from Trip t
        left join TripParticipant tp on tp.trip = t and tp.deletedAt is null
        where t.deletedAt is null
          and (t.ownerUser.id = :userId or tp.user.id = :userId)
          and (:status is null or t.tripStatus = :status)
          and (
            t.createdAt < :cursorCreatedAt
            or (t.createdAt = :cursorCreatedAt and t.id < :cursorId)
          )
        order by t.createdAt desc, t.id desc
        """
    )
    fun findAccessibleTripsAfterCursor(
        @Param("userId") userId: Long,
        @Param("status") status: TripStatus?,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant,
        @Param("cursorId") cursorId: Long,
        pageable: Pageable,
    ): List<Trip>
}
