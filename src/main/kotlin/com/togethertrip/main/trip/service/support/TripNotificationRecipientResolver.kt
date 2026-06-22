package com.togethertrip.main.trip.service.support

import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.UserStatus
import org.springframework.stereotype.Component

@Component
class TripNotificationRecipientResolver(
    private val tripParticipantRepository: TripParticipantRepository,
) {

    fun findActiveUserIds(
        tripId: Long,
        actorUserId: Long? = null,
    ): List<Long> {
        return tripParticipantRepository.findActiveUserIdsForNotification(
            tripId = tripId,
            participantStatus = TripParticipantStatus.ACTIVE.name,
            userStatus = UserStatus.ACTIVE.name,
        )
            .asSequence()
            .filter { userId -> actorUserId == null || userId != actorUserId }
            .distinct()
            .toList()
    }

    fun resolveSingleUserId(
        userId: Long?,
        actorUserId: Long? = null,
    ): Long? {
        return userId?.takeIf { recipientUserId -> actorUserId == null || recipientUserId != actorUserId }
    }
}
