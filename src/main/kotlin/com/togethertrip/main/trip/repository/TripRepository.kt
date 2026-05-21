package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.Trip
import org.springframework.data.jpa.repository.JpaRepository

interface TripRepository : JpaRepository<Trip, Long>
