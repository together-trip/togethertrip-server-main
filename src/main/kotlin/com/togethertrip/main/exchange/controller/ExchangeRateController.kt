package com.togethertrip.main.exchange.controller

import com.togethertrip.main.exchange.controller.spec.ExchangeRateApiSpec
import com.togethertrip.main.exchange.dto.response.ExchangeRateSearchResponse
import com.togethertrip.main.exchange.service.ExchangeRateQueryService
import com.togethertrip.main.global.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/exchange-rates")
class ExchangeRateController(
    private val exchangeRateQueryService: ExchangeRateQueryService,
) : ExchangeRateApiSpec {

    @GetMapping
    override fun getExchangeRates(
        @RequestParam(required = false) baseCurrency: String?,
        @RequestParam(required = false) targetCurrencies: String?,
        @RequestParam(required = false) date: LocalDate?,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
    ): ApiResponse<ExchangeRateSearchResponse> {
        return ApiResponse.success(
            exchangeRateQueryService.search(
                baseCurrency = baseCurrency,
                targetCurrencies = targetCurrencies,
                date = date,
                from = from,
                to = to,
            )
        )
    }
}
