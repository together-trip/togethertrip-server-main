package com.togethertrip.main.exchange.client

import com.togethertrip.main.exchange.config.ExchangeRateProperties
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class KoreaEximExchangeRateClient(
    private val webClientBuilder: WebClient.Builder,
    private val properties: ExchangeRateProperties,
) {

    fun fetchRates(rateDate: LocalDate): KoreaEximExchangeRateFetchResult {
        if (properties.koreaExim.authKey.isBlank()) {
            throw KoreaEximExchangeRateException("한국수출입은행 환율 API auth-key가 설정되지 않았습니다.")
        }

        val responses = webClientBuilder
            .baseUrl(properties.koreaExim.baseUrl)
            .build()
            .get()
            .uri { builder ->
                builder
                    .queryParam("authkey", properties.koreaExim.authKey)
                    .queryParam("searchdate", rateDate.format(SEARCH_DATE_FORMATTER))
                    .queryParam("data", properties.koreaExim.dataCode)
                    .build()
            }
            .retrieve()
            .bodyToFlux(KoreaEximExchangeRateResponse::class.java)
            .collectList()
            .block(properties.koreaExim.timeout)
            .orEmpty()

        if (responses.isEmpty()) {
            return KoreaEximExchangeRateFetchResult.NoData
        }

        val resultCode = responses.first().result
        if (resultCode != null && resultCode != SUCCESS_RESULT_CODE) {
            return KoreaEximExchangeRateFetchResult.Failed(
                KoreaEximExchangeRateResultCode.from(resultCode)
            )
        }

        return KoreaEximExchangeRateFetchResult.Success(responses)
    }

    companion object {
        private const val SUCCESS_RESULT_CODE = 1
        private val SEARCH_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }
}
