package com.togethertrip.main.exchange.client

sealed interface KoreaEximExchangeRateFetchResult {
    data class Success(val responses: List<KoreaEximExchangeRateResponse>) : KoreaEximExchangeRateFetchResult
    data object NoData : KoreaEximExchangeRateFetchResult
    data class Failed(val resultCode: KoreaEximExchangeRateResultCode) : KoreaEximExchangeRateFetchResult
}
