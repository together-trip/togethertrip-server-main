package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.Trip
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TripRepository : JpaRepository<Trip, Long>, TripQueryRepository {

    fun findByIdAndDeletedAtIsNull(id: Long): Trip?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id and t.deletedAt is null")
    fun findByIdAndDeletedAtIsNullForUpdate(@Param("id") id: Long): Trip?

}
