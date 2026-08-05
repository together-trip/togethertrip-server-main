package com.togethertrip.main.auth.client

import io.netty.channel.ChannelOption
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeoutException

internal object AppleOAuthWebClient {
    fun build(
        builder: WebClient.Builder,
        properties: AppleOAuthProperties,
    ): WebClient {
        val httpClient = HttpClient.create()
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                properties.connectTimeout.toMillis().toInt(),
            )
            .responseTimeout(properties.responseTimeout)

        return builder.clone()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    fun isTimeout(exception: Throwable): Boolean {
        var cause: Throwable? = exception
        while (cause != null) {
            if (cause is TimeoutException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }
}
