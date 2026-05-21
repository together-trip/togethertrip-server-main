package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.TripCountry
import org.springframework.data.jpa.repository.JpaRepository

interface TripCountryRepository : JpaRepository<TripCountry, Long>
