package com.togethertrip.main.global.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    AUTHENTICATION_REQUIRED(
        HttpStatus.UNAUTHORIZED,
        "인증이 필요합니다.",
    ),

    ACCESS_DENIED(
        HttpStatus.FORBIDDEN,
        "접근 권한이 없습니다.",
    ),

    INVALID_INPUT(
        HttpStatus.BAD_REQUEST,
        "잘못된 입력입니다.",
    ),

    INVALID_ACCESS_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "유효하지 않은 access token입니다.",
    ),

    INVALID_REFRESH_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "유효하지 않은 refresh token입니다.",
    ),

    EXPIRED_REFRESH_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "만료된 refresh token입니다.",
    ),

    INVALID_OAUTH_TOKEN(
        HttpStatus.BAD_REQUEST,
        "유효하지 않은 OAuth token입니다.",
    ),

    OAUTH_USER_INFO_FAILED(
        HttpStatus.BAD_REQUEST,
        "OAuth 사용자 정보 조회에 실패했습니다.",
    ),

    USER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "사용자를 찾을 수 없습니다.",
    ),

    INACTIVE_USER(
        HttpStatus.FORBIDDEN,
        "활성 상태의 사용자가 아닙니다.",
    ),

    PHONE_VERIFICATION_REQUIRED(
        HttpStatus.FORBIDDEN,
        "전화번호 인증이 필요합니다.",
    ),

    INVALID_PHONE_NUMBER(
        HttpStatus.BAD_REQUEST,
        "유효하지 않은 전화번호입니다.",
    ),

    PHONE_NUMBER_ALREADY_USED(
        HttpStatus.CONFLICT,
        "이미 사용 중인 전화번호입니다.",
    ),

    PHONE_VERIFICATION_TOKEN_EXPIRED(
        HttpStatus.UNAUTHORIZED,
        "전화번호 인증 세션이 만료되었습니다.",
    ),

    PHONE_VERIFICATION_CODE_EXPIRED(
        HttpStatus.BAD_REQUEST,
        "인증번호가 만료되었습니다.",
    ),

    INVALID_PHONE_VERIFICATION_CODE(
        HttpStatus.BAD_REQUEST,
        "인증번호가 일치하지 않습니다.",
    ),

    PHONE_VERIFICATION_ATTEMPT_EXCEEDED(
        HttpStatus.BAD_REQUEST,
        "인증 시도 횟수를 초과했습니다. 다시 요청해주세요.",
    ),

    PHONE_VERIFICATION_REQUEST_TOO_SOON(
        HttpStatus.TOO_MANY_REQUESTS,
        "인증번호는 1분 후 다시 요청할 수 있습니다.",
    ),

    PHONE_VERIFICATION_DAILY_LIMIT_EXCEEDED(
        HttpStatus.TOO_MANY_REQUESTS,
        "오늘 인증번호 요청 가능 횟수를 초과했습니다.",
    ),

    SMS_CONFIGURATION_REQUIRED(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "SMS 발송 설정이 필요합니다.",
    ),

    SMS_SEND_FAILED(
        HttpStatus.BAD_GATEWAY,
        "SMS 발송에 실패했습니다.",
    ),

    TRIP_PARTICIPANT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "여행 참여자 정보를 찾을 수 없습니다.",
    ),

    INTERNAL_SERVER_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "서버 오류가 발생했습니다.",
    )
}
