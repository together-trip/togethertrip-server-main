package com.togethertrip.main.exchange.controller.spec

import com.togethertrip.main.exchange.dto.response.ExchangeRateSearchResponse
import com.togethertrip.main.global.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.LocalDate

@Tag(name = "Exchange Rate", description = "환율 API")
@SecurityRequirement(name = "bearerAuth")
interface ExchangeRateApiSpec {

    @Operation(
        summary = "전역 환율 조회",
        description = "KRW 기준 전역 환율을 단일 날짜 또는 날짜 기간으로 조회합니다.",
    )
    fun getExchangeRates(
        baseCurrency: String?,
        targetCurrencies: String?,
        date: LocalDate?,
        from: LocalDate?,
        to: LocalDate?,
    ): ApiResponse<ExchangeRateSearchResponse>
}
