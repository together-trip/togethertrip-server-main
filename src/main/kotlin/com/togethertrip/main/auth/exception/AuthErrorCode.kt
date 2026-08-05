package com.togethertrip.main.auth.exception

import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class AuthErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    INVALID_ACCESS_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "INVALID_ACCESS_TOKEN",
        "유효하지 않은 access token입니다.",
    ),

    INVALID_REFRESH_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "INVALID_REFRESH_TOKEN",
        "유효하지 않은 refresh token입니다.",
    ),

    EXPIRED_REFRESH_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "EXPIRED_REFRESH_TOKEN",
        "만료된 refresh token입니다.",
    ),

    INVALID_OAUTH_TOKEN(
        HttpStatus.BAD_REQUEST,
        "INVALID_OAUTH_TOKEN",
        "유효하지 않은 OAuth token입니다.",
    ),

    OAUTH_USER_INFO_FAILED(
        HttpStatus.BAD_REQUEST,
        "OAUTH_USER_INFO_FAILED",
        "OAuth 사용자 정보 조회에 실패했습니다.",
    ),

    INVALID_APPLE_IDENTITY_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "INVALID_APPLE_IDENTITY_TOKEN",
        "유효하지 않은 Apple identity token입니다.",
    ),

    APPLE_NONCE_REUSED(
        HttpStatus.UNAUTHORIZED,
        "APPLE_NONCE_REUSED",
        "이미 사용된 Apple 로그인 요청입니다.",
    ),

    APPLE_AUTHORIZATION_FAILED(
        HttpStatus.BAD_GATEWAY,
        "APPLE_AUTHORIZATION_FAILED",
        "Apple 인증 서버와 통신하지 못했습니다.",
    ),

    SIGNUP_CONFIRMATION_IN_PROGRESS(
        HttpStatus.CONFLICT,
        "SIGNUP_CONFIRMATION_IN_PROGRESS",
        "회원가입 처리가 진행 중입니다. 잠시 후 다시 시도해주세요.",
    )
}
