package com.togethertrip.main.triprecap.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.service.support.TripAccessResolver
import com.togethertrip.main.triprecap.domain.TripRecap
import com.togethertrip.main.triprecap.domain.TripRecapStatus
import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.triprecap.dto.response.TripRecapCreateResponse
import com.togethertrip.main.triprecap.dto.response.TripRecapResponse
import com.togethertrip.main.triprecap.dto.response.TripRecapStatusResponse
import com.togethertrip.main.triprecap.exception.TripRecapErrorCode
import com.togethertrip.main.triprecap.repository.TripRecapRepository
import com.togethertrip.main.triprecap.repository.TripRecapSceneRepository
import com.togethertrip.main.triprecap.service.storage.TripRecapImageStorage
import com.togethertrip.main.triprecap.service.storage.TripRecapStoredImageFile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class TripRecapService(
    private val tripAccessResolver: TripAccessResolver,
    private val tripRecapRepository: TripRecapRepository,
    private val tripRecapSceneRepository: TripRecapSceneRepository,
    private val tripRecapAvailabilityPolicy: TripRecapAvailabilityPolicy,
    private val tripRecapGenerationJobLauncher: TripRecapGenerationJobLauncher,
    private val tripRecapImageStorage: TripRecapImageStorage,
) {

    fun getStatus(
        userId: Long,
        tripId: Long,
    ): TripRecapStatusResponse {
        val trip = tripAccessResolver.getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )

        if (!tripRecapAvailabilityPolicy.isAvailable(trip)) {
            return TripRecapStatusResponse.unavailable()
        }

        val recap = tripRecapRepository.findByTripIdAndDeletedAtIsNull(trip.id)
            ?: return TripRecapStatusResponse.none()

        return TripRecapStatusResponse.from(
            recapId = recap.id,
            status = recap.status,
            style = recap.style,
        )
    }

    @Transactional
    fun create(
        userId: Long,
        tripId: Long,
        style: TripRecapStyle,
    ): TripRecapCreateResponse {
        val user = tripAccessResolver.getActiveUser(userId)
        val trip = tripAccessResolver.getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        assertAvailable(trip)

        val existingRecap = tripRecapRepository.findByTripIdAndDeletedAtIsNull(trip.id)
        if (existingRecap != null) {
            return existingRecap.toCreateResponse()
        }

        val recap = tripRecapRepository.save(
            TripRecap(
                trip = trip,
                requestedBy = user,
                style = style,
            )
        )
        tripRecapGenerationJobLauncher.launchAfterCommit(recap.id)

        return recap.toCreateResponse()
    }

    @Transactional
    fun retry(
        userId: Long,
        tripId: Long,
        style: TripRecapStyle,
    ): TripRecapCreateResponse {
        val user = tripAccessResolver.getActiveUser(userId)
        val trip = tripAccessResolver.getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        assertAvailable(trip)

        val recap = tripRecapRepository.findByTripIdAndDeletedAtIsNull(trip.id)
            ?: throw BusinessException(TripRecapErrorCode.TRIP_RECAP_NOT_FOUND)

        if (!recap.isFailed()) {
            throw BusinessException(TripRecapErrorCode.TRIP_RECAP_RETRY_NOT_ALLOWED)
        }

        tripRecapSceneRepository.softDeleteByRecapId(
            recapId = recap.id,
            deletedAt = Instant.now(),
        )
        recap.retry(
            style = style,
            requestedBy = user,
            now = Instant.now(),
        )
        tripRecapGenerationJobLauncher.launchAfterCommit(recap.id)

        return recap.toCreateResponse()
    }

    fun getRecap(
        userId: Long,
        tripId: Long,
    ): TripRecapResponse {
        val trip = tripAccessResolver.getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        val recap = tripRecapRepository.findByTripIdAndDeletedAtIsNull(trip.id)
            ?: throw BusinessException(TripRecapErrorCode.TRIP_RECAP_NOT_FOUND)

        if (recap.status != TripRecapStatus.COMPLETED) {
            throw BusinessException(TripRecapErrorCode.TRIP_RECAP_NOT_COMPLETED)
        }

        val scenes = tripRecapSceneRepository.findByRecapIdAndDeletedAtIsNullOrderBySceneOrderAsc(recap.id)

        return TripRecapResponse.from(
            recap = recap,
            scenes = scenes,
        )
    }

    fun getSceneImage(
        userId: Long,
        tripId: Long,
        sceneId: Long,
    ): TripRecapStoredImageFile {
        val trip = tripAccessResolver.getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        val recap = tripRecapRepository.findByTripIdAndDeletedAtIsNull(trip.id)
            ?: throw BusinessException(TripRecapErrorCode.TRIP_RECAP_NOT_FOUND)

        if (recap.status != TripRecapStatus.COMPLETED) {
            throw BusinessException(TripRecapErrorCode.TRIP_RECAP_NOT_COMPLETED)
        }

        val scene = tripRecapSceneRepository.findByIdAndRecapIdAndDeletedAtIsNull(
            id = sceneId,
            recapId = recap.id,
        ) ?: throw BusinessException(TripRecapErrorCode.TRIP_RECAP_SCENE_NOT_FOUND)

        return tripRecapImageStorage.load(scene.imageObjectKey)
    }

    private fun assertAvailable(trip: Trip) {
        if (!tripRecapAvailabilityPolicy.isAvailable(trip)) {
            throw BusinessException(TripRecapErrorCode.TRIP_RECAP_NOT_AVAILABLE)
        }
    }

    private fun TripRecap.toCreateResponse(): TripRecapCreateResponse {
        return TripRecapCreateResponse(
            recapId = id,
            status = status,
        )
    }
}
