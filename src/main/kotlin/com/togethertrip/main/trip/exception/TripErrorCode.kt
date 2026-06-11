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

    TRIP_INVITATION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "TRIP_INVITATION_NOT_FOUND",
        "여행 초대 정보를 찾을 수 없습니다.",
    ),

    INVALID_TRIP_INVITATION_LOOKUP(
        HttpStatus.BAD_REQUEST,
        "INVALID_TRIP_INVITATION_LOOKUP",
        "초대 코드 또는 초대 토큰 중 하나만 전달해야 합니다.",
    ),

    TRIP_INVITATION_NOT_ACTIVE(
        HttpStatus.CONFLICT,
        "TRIP_INVITATION_NOT_ACTIVE",
        "사용할 수 없는 여행 초대입니다.",
    ),

    TRIP_INVITATION_EXPIRED(
        HttpStatus.GONE,
        "TRIP_INVITATION_EXPIRED",
        "만료된 여행 초대입니다.",
    ),

    TRIP_ALREADY_JOINED(
        HttpStatus.CONFLICT,
        "TRIP_ALREADY_JOINED",
        "이미 참여 중인 여행입니다.",
    ),

    TRIP_JOIN_CLOSED(
        HttpStatus.CONFLICT,
        "TRIP_JOIN_CLOSED",
        "정산이 시작된 여행에는 새로 참여할 수 없습니다.",
    ),

    INVALID_TRIP_STATUS(
        HttpStatus.BAD_REQUEST,
        "INVALID_TRIP_STATUS",
        "유효하지 않은 여행 상태입니다.",
    ),

    UNSUPPORTED_TRIP_COUNTRY_CURRENCY(
        HttpStatus.BAD_REQUEST,
        "UNSUPPORTED_TRIP_COUNTRY_CURRENCY",
        "지원하지 않는 여행 국가 통화입니다.",
    ),

    EXCHANGE_RATE_FETCH_FAILED(
        HttpStatus.BAD_GATEWAY,
        "EXCHANGE_RATE_FETCH_FAILED",
        "환율 조회에 실패했습니다.",
    ),

    EXCHANGE_RATE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "EXCHANGE_RATE_NOT_FOUND",
        "여행 환율 정보를 찾을 수 없습니다.",
    ),

    INVALID_EXCHANGE_RATE(
        HttpStatus.BAD_REQUEST,
        "INVALID_EXCHANGE_RATE",
        "유효하지 않은 환율입니다.",
    )
}
