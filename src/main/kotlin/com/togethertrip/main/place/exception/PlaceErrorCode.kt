package com.togethertrip.main.place.exception

import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class PlaceErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    PLACE_SEARCH_NOT_CONFIGURED(
        HttpStatus.SERVICE_UNAVAILABLE,
        "PLACE_SEARCH_NOT_CONFIGURED",
        "장소 검색이 설정되지 않았습니다.",
    ),
    PLACE_SEARCH_FAILED(
        HttpStatus.BAD_GATEWAY,
        "PLACE_SEARCH_FAILED",
        "장소 검색 제공자 호출에 실패했습니다.",
    ),
    PLACE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "PLACE_NOT_FOUND",
        "장소를 찾을 수 없습니다.",
    ),
    PLACE_RATE_LIMIT_EXCEEDED(
        HttpStatus.TOO_MANY_REQUESTS,
        "PLACE_RATE_LIMIT_EXCEEDED",
        "장소 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.",
    ),
}
