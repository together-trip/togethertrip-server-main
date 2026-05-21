package com.togethertrip.main.trip.service

import com.togethertrip.main.trip.repository.TripParticipantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TripParticipantService(
    private val tripParticipantRepository: TripParticipantRepository,
)
