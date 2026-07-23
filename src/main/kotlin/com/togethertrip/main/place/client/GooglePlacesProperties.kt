package com.togethertrip.main.place.client

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "place.google")
class GooglePlacesProperties {
    var placesBaseUrl: String = "https://places.googleapis.com"
    var geocodingBaseUrl: String = "https://maps.googleapis.com"
    var apiKey: String = ""
    var timeout: Duration = Duration.ofSeconds(5)
}
