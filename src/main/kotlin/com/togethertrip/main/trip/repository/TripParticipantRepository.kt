package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.TripParticipant
import org.springframework.data.jpa.repository.JpaRepository

interface TripParticipantRepository : JpaRepository<TripParticipant, Long>
