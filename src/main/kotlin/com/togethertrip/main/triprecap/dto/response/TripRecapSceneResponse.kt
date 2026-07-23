package com.togethertrip.main.triprecap.dto.response

import com.togethertrip.main.triprecap.domain.TripRecapScene

data class TripRecapSceneResponse(
    val sceneId: Long,
    val order: Int,
    val imageUrl: String,
    val aspectRatio: String,
) {
    companion object {
        fun from(
            tripId: Long,
            scene: TripRecapScene,
        ): TripRecapSceneResponse {
            return TripRecapSceneResponse(
                sceneId = scene.id,
                order = scene.sceneOrder,
                imageUrl = "/api/trips/$tripId/recap/scenes/${scene.id}/image",
                aspectRatio = "9:16",
            )
        }
    }
}
