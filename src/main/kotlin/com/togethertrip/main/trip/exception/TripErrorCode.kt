package com.togethertrip.main.trip.exception

import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class TripErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    TRIP_PARTICIPANT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "TRIP_PARTICIPANT_NOT_FOUND",
        "여행 참여자 정보를 찾을 수 없습니다.",
    )
}
