package com.togethertrip.main.triprecap.repository

import com.togethertrip.main.triprecap.domain.TripRecap
import org.springframework.data.jpa.repository.JpaRepository

interface TripRecapRepository : JpaRepository<TripRecap, Long> {

    fun findByTripIdAndDeletedAtIsNull(tripId: Long): TripRecap?

    fun findByIdAndDeletedAtIsNull(id: Long): TripRecap?
}
