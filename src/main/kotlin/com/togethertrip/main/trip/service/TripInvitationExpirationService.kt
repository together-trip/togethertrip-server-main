package com.togethertrip.main.trip.service

import com.togethertrip.main.trip.repository.TripInvitationRepository
import com.togethertrip.main.trip.domain.TripInvitationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TripInvitationExpirationService(
    private val tripInvitationRepository: TripInvitationRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markExpiredIfActive(
        invitationId: Long,
        now: Instant,
    ) {
        tripInvitationRepository.markExpiredIfActive(
            invitationId = invitationId,
            now = now,
            activeStatus = TripInvitationStatus.ACTIVE,
            expiredStatus = TripInvitationStatus.EXPIRED,
        )
    }
}
