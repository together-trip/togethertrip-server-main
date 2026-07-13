package com.togethertrip.main.triprecap.service

import com.togethertrip.main.triprecap.service.ai.TripRecapGenerator
import com.togethertrip.main.triprecap.service.storage.TripRecapImageStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TripRecapGenerationService(
    private val tripRecapGenerationContextLoader: TripRecapGenerationContextLoader,
    private val tripRecapGenerator: TripRecapGenerator,
    private val tripRecapImageStorage: TripRecapImageStorage,
    private val tripRecapGenerationCompletionService: TripRecapGenerationCompletionService,
) {

    private val logger = LoggerFactory.getLogger(TripRecapGenerationService::class.java)

    fun generate(recapId: Long) {
        try {
            val context = tripRecapGenerationContextLoader.load(recapId) ?: return
            val result = tripRecapGenerator.generate(context.request)
            validateSceneCount(result.scenes.size)
            val storedScenes = result.scenes
                .sortedBy { it.order }
                .map { scene ->
                    val storedImage = tripRecapImageStorage.store(
                        tripId = context.tripId,
                        recapId = context.recapId,
                        sceneOrder = scene.order,
                        imageBytes = scene.imageBytes,
                    )
                    TripRecapStoredScene(
                        order = scene.order,
                        sceneDescription = scene.sceneDescription,
                        imagePrompt = scene.imagePrompt,
                        imageObjectKey = storedImage.objectKey,
                        imageUrl = storedImage.imageUrl,
                        generationProvider = result.provider,
                        generationModel = result.model,
                    )
                }

            tripRecapGenerationCompletionService.complete(
                recapId = recapId,
                scenes = storedScenes,
            )
        } catch (exception: Throwable) {
            logger.warn(
                "trip recap generation failed recapId={} exception={} message={}",
                recapId,
                exception::class.simpleName,
                exception.message,
            )
            tripRecapGenerationCompletionService.fail(
                recapId = recapId,
                exception = exception,
            )
        }
    }

    private fun validateSceneCount(sceneCount: Int) {
        require(sceneCount in MIN_SCENE_COUNT..MAX_SCENE_COUNT) {
            "trip recap scene count must be between $MIN_SCENE_COUNT and $MAX_SCENE_COUNT"
        }
    }

    companion object {
        private const val MIN_SCENE_COUNT = 3
        private const val MAX_SCENE_COUNT = 7
    }
}
