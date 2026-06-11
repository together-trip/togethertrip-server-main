package com.togethertrip.main.exchange.client

import com.fasterxml.jackson.annotation.JsonProperty

data class KoreaEximExchangeRateResponse(
    val result: Int? = null,
    @JsonProperty("cur_unit")
    val currencyUnit: String? = null,
    @JsonProperty("deal_bas_r")
    val dealBaseRate: String? = null,
)
