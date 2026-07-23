package com.togethertrip.main.triprecap.exception

import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class TripRecapErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    TRIP_RECAP_NOT_AVAILABLE(
        HttpStatus.BAD_REQUEST,
        "TRIP_RECAP_NOT_AVAILABLE",
        "여행 종료 및 정산 완료 후 Recap을 만들 수 있습니다.",
    ),
    TRIP_RECAP_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "TRIP_RECAP_NOT_FOUND",
        "Recap을 찾을 수 없습니다.",
    ),
    TRIP_RECAP_ALREADY_CREATING(
        HttpStatus.CONFLICT,
        "TRIP_RECAP_ALREADY_CREATING",
        "Recap 생성이 이미 진행 중입니다.",
    ),
    TRIP_RECAP_ALREADY_COMPLETED(
        HttpStatus.CONFLICT,
        "TRIP_RECAP_ALREADY_COMPLETED",
        "이미 완성된 Recap이 있습니다.",
    ),
    TRIP_RECAP_RETRY_NOT_ALLOWED(
        HttpStatus.BAD_REQUEST,
        "TRIP_RECAP_RETRY_NOT_ALLOWED",
        "실패한 Recap만 다시 시도할 수 있습니다.",
    ),
    TRIP_RECAP_NOT_COMPLETED(
        HttpStatus.BAD_REQUEST,
        "TRIP_RECAP_NOT_COMPLETED",
        "아직 완성되지 않은 Recap입니다.",
    ),
    TRIP_RECAP_SCENE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "TRIP_RECAP_SCENE_NOT_FOUND",
        "Recap 장면을 찾을 수 없습니다.",
    ),
}
