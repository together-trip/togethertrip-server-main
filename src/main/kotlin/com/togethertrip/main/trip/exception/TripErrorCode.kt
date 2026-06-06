package com.togethertrip.main.trip.exception

import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class TripErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    TRIP_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "TRIP_NOT_FOUND",
        "여행 정보를 찾을 수 없습니다.",
    ),

    TRIP_PARTICIPANT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "TRIP_PARTICIPANT_NOT_FOUND",
        "여행 참여자 정보를 찾을 수 없습니다.",
    ),

    TRIP_ACCESS_DENIED(
        HttpStatus.FORBIDDEN,
        "TRIP_ACCESS_DENIED",
        "해당 여행에 접근할 권한이 없습니다.",
    ),

    TRIP_OWNER_ONLY(
        HttpStatus.FORBIDDEN,
        "TRIP_OWNER_ONLY",
        "여행 방장만 수행할 수 있는 작업입니다.",
    ),

    INVALID_TRIP_STATUS(
        HttpStatus.BAD_REQUEST,
        "INVALID_TRIP_STATUS",
        "유효하지 않은 여행 상태입니다.",
    )
}
