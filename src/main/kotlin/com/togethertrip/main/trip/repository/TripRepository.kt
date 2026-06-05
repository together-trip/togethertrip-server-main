package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TripRepository : JpaRepository<Trip, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Trip?

    @Query(
        """
        select distinct t
        from Trip t
        left join TripParticipant tp on tp.trip = t and tp.deletedAt is null
        where t.deletedAt is null
          and (t.ownerUser.id = :userId or tp.user.id = :userId)
          and (:status is null or t.tripStatus = :status)
        """
    )
    fun findAccessibleTrips(
        @Param("userId") userId: Long,
        @Param("status") status: TripStatus?,
        pageable: Pageable,
    ): Page<Trip>
}
