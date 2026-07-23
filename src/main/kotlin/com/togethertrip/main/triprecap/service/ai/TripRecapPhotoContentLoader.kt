package com.togethertrip.main.triprecap.service.ai

fun interface TripRecapPhotoContentLoader {

    fun load(reference: TripRecapPhotoReference): TripRecapPhotoContent?
}

data class TripRecapPhotoContent(
    val filename: String,
    val contentType: String,
    val bytes: ByteArray,
)
