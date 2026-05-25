package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.TripExchangeRate
import org.springframework.data.jpa.repository.JpaRepository

interface TripExchangeRateRepository : JpaRepository<TripExchangeRate, Long>
