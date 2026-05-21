package com.togethertrip.main.trip.service

import com.togethertrip.main.trip.repository.TripRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TripInviteService(
    private val tripRepository: TripRepository,
)
