package com.togethertrip.main.exchange.client

enum class KoreaEximExchangeRateResultCode(
    val code: Int,
    val description: String,
) {
    INVALID_DATA_CODE(2, "환율 API data code 오류"),
    INVALID_AUTH_KEY(3, "환율 API 인증키 오류"),
    DAILY_LIMIT_EXCEEDED(4, "환율 API 일일 제한 초과"),
    UNKNOWN(-1, "알 수 없는 환율 API 오류"),
    ;

    companion object {
        fun from(code: Int): KoreaEximExchangeRateResultCode {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }
    }
}
