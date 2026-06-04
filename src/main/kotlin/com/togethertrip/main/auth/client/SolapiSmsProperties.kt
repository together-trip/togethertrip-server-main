package com.togethertrip.main.auth.client

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "sms.solapi")
class SolapiSmsProperties {
    var apiKey: String = ""
    var apiSecret: String = ""
    var from: String = ""
    var baseUrl: String = "https://api.solapi.com"
}
