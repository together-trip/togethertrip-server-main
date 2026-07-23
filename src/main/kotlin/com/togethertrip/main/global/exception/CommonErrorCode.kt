package com.togethertrip.main.global.exception

import org.springframework.http.HttpStatus

enum class CommonErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    AUTHENTICATION_REQUIRED(
        HttpStatus.UNAUTHORIZED,
        "AUTHENTICATION_REQUIRED",
        "인증이 필요합니다.",
    ),

    ACCESS_DENIED(
        HttpStatus.FORBIDDEN,
        "ACCESS_DENIED",
        "접근 권한이 없습니다.",
    ),

    INVALID_INPUT(
        HttpStatus.BAD_REQUEST,
        "INVALID_INPUT",
        "잘못된 입력입니다.",
    ),

    INVALID_PHONE_NUMBER(
        HttpStatus.BAD_REQUEST,
        "INVALID_PHONE_NUMBER",
        "유효하지 않은 전화번호입니다.",
    ),

    CONCURRENT_MODIFICATION(
        HttpStatus.CONFLICT,
        "CONCURRENT_MODIFICATION",
        "동시에 변경된 데이터가 있습니다. 다시 시도해주세요.",
    ),

    INTERNAL_SERVER_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_SERVER_ERROR",
        "서버 오류가 발생했습니다.",
    )
}
