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

    INTERNAL_SERVER_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "서버 오류가 발생했습니다.",
    )
}