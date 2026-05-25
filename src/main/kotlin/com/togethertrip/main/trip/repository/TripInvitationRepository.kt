package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.TripInvitation
import org.springframework.data.jpa.repository.JpaRepository

interface TripInvitationRepository : JpaRepository<TripInvitation, Long>
