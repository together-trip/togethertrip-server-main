package com.togethertrip.main.triprecap.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.triprecap.exception.TripRecapErrorCode
import com.togethertrip.main.triprecap.repository.TripRecapRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TripRecapGenerationContextLoader(
    private val tripRecapRepository: TripRecapRepository,
    private val tripRecapDataCollector: TripRecapDataCollector,
) {

    fun load(recapId: Long): TripRecapGenerationContext? {
        val recap = tripRecapRepository.findByIdAndDeletedAtIsNull(recapId)
            ?: throw BusinessException(TripRecapErrorCode.TRIP_RECAP_NOT_FOUND)

        if (!recap.isCreating()) {
            return null
        }

        return TripRecapGenerationContext(
            recapId = recap.id,
            tripId = recap.trip.id,
            style = recap.style,
            request = tripRecapDataCollector.collect(
                trip = recap.trip,
                style = recap.style,
            ),
        )
    }
}
