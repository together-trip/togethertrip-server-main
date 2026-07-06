package com.togethertrip.main.triprecap.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.outbox.domain.OutboxAggregateType
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.common.DefaultOutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.triprecap.TripRecapCompletedPayload
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.triprecap.domain.TripRecapScene
import com.togethertrip.main.triprecap.exception.TripRecapErrorCode
import com.togethertrip.main.triprecap.repository.TripRecapRepository
import com.togethertrip.main.triprecap.repository.TripRecapSceneRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.UserStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TripRecapGenerationCompletionService(
    private val tripRecapRepository: TripRecapRepository,
    private val tripRecapSceneRepository: TripRecapSceneRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val outboxEventPublisher: OutboxEventPublisher,
) {

    @Transactional
    fun complete(
        recapId: Long,
        scenes: List<TripRecapStoredScene>,
        completedAt: Instant = Instant.now(),
    ) {
        val recap = tripRecapRepository.findByIdAndDeletedAtIsNull(recapId)
            ?: throw BusinessException(TripRecapErrorCode.TRIP_RECAP_NOT_FOUND)

        if (!recap.isCreating()) {
            return
        }

        tripRecapSceneRepository.softDeleteByRecapId(
            recapId = recap.id,
            deletedAt = completedAt,
        )
        scenes.sortedBy { it.order }.forEach { scene ->
            tripRecapSceneRepository.save(
                TripRecapScene(
                    recap = recap,
                    sceneOrder = scene.order,
                    style = recap.style,
                    imageObjectKey = scene.imageObjectKey,
                    imageUrl = scene.imageUrl,
                    sceneDescription = scene.sceneDescription,
                    imagePrompt = scene.imagePrompt,
                    generationProvider = scene.generationProvider,
                    generationModel = scene.generationModel,
                )
            )
        }

        recap.complete(
            sceneCount = scenes.size,
            now = completedAt,
        )
        publishCompletedEvent(
            recapId = recap.id,
            tripId = recap.trip.id,
            tripName = recap.trip.title,
            occurredAt = completedAt,
        )
    }

    @Transactional
    fun fail(
        recapId: Long,
        exception: Throwable,
        failedAt: Instant = Instant.now(),
    ) {
        val recap = tripRecapRepository.findByIdAndDeletedAtIsNull(recapId)
            ?: return

        if (recap.isCreating()) {
            recap.fail(
                reason = exception.message ?: exception::class.simpleName ?: "unknown generation failure",
                now = failedAt,
            )
        }
    }

    private fun publishCompletedEvent(
        recapId: Long,
        tripId: Long,
        tripName: String,
        occurredAt: Instant,
    ) {
        val recipients = tripParticipantRepository.findActiveUserIdsForNotification(
            tripId = tripId,
            participantStatus = TripParticipantStatus.ACTIVE.name,
            userStatus = UserStatus.ACTIVE.name,
        ).map(::DefaultOutboxRecipientPayload)

        outboxEventPublisher.publish(
            aggregateType = OutboxAggregateType.TRIP_RECAP,
            aggregateId = recapId,
            eventType = OutboxEventType.TRIP_RECAP_COMPLETED,
            payload = TripRecapCompletedPayload(
                recipients = recipients,
                tripId = tripId,
                tripRecapId = recapId,
                tripName = tripName,
                occurredAt = occurredAt,
            ),
        )
    }
}
