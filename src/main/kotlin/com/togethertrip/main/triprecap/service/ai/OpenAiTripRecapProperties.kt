package com.togethertrip.main.triprecap.service.ai

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "trip-recap.ai.openai")
class OpenAiTripRecapProperties {
    var baseUrl: String = "https://api.openai.com"
    var apiKey: String = ""
    var model: String = "gpt-image-2"
    var outputProfile: TripRecapImageOutputProfile = TripRecapImageOutputProfile.ECONOMY
    var size: String = "1152x2048"
    var quality: String = "medium"
    var timeout: Duration = Duration.ofMinutes(3)
    var maxReferenceImages: Int = 1
    var maxReferenceDimension: Int = 1_024
    var cacheEnabled: Boolean = true
    var cacheTtl: Duration = Duration.ofMinutes(15)
    var cacheLockWait: Duration = Duration.ofMinutes(6)

    fun outputSettings(): TripRecapImageOutputSettings = outputProfile.resolve(size, quality)
}
