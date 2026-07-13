package com.togethertrip.main.triprecap.service.storage

interface TripRecapImageStorage {

    fun store(
        tripId: Long,
        recapId: Long,
        sceneOrder: Int,
        imageBytes: ByteArray,
    ): TripRecapStoredImage

    fun load(objectKey: String): TripRecapStoredImageFile
}
