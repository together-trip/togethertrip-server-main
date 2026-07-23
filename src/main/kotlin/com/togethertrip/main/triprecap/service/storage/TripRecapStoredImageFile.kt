package com.togethertrip.main.triprecap.service.storage

data class TripRecapStoredImageFile(
    val bytes: ByteArray,
    val contentType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is TripRecapStoredImageFile) {
            return false
        }

        return bytes.contentEquals(other.bytes) && contentType == other.contentType
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}
