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

    PHONE_VERIFICATION_REQUIRED(
        HttpStatus.FORBIDDEN,
        "PHONE_VERIFICATION_REQUIRED",
        "전화번호 인증이 필요합니다.",
    ),

    PHONE_NUMBER_ALREADY_USED(
        HttpStatus.CONFLICT,
        "PHONE_NUMBER_ALREADY_USED",
        "이미 사용 중인 전화번호입니다.",
    ),

    PHONE_VERIFICATION_TOKEN_EXPIRED(
        HttpStatus.UNAUTHORIZED,
        "PHONE_VERIFICATION_TOKEN_EXPIRED",
        "전화번호 인증 세션이 만료되었습니다.",
    ),

    PHONE_VERIFICATION_CODE_EXPIRED(
        HttpStatus.BAD_REQUEST,
        "PHONE_VERIFICATION_CODE_EXPIRED",
        "인증번호가 만료되었습니다.",
    ),

    INVALID_PHONE_VERIFICATION_CODE(
        HttpStatus.BAD_REQUEST,
        "INVALID_PHONE_VERIFICATION_CODE",
        "인증번호가 일치하지 않습니다.",
    ),

    PHONE_VERIFICATION_ATTEMPT_EXCEEDED(
        HttpStatus.BAD_REQUEST,
        "PHONE_VERIFICATION_ATTEMPT_EXCEEDED",
        "인증 시도 횟수를 초과했습니다. 다시 요청해주세요.",
    ),

    PHONE_VERIFICATION_REQUEST_TOO_SOON(
        HttpStatus.TOO_MANY_REQUESTS,
        "PHONE_VERIFICATION_REQUEST_TOO_SOON",
        "인증번호는 1분 후 다시 요청할 수 있습니다.",
    ),

    PHONE_VERIFICATION_DAILY_LIMIT_EXCEEDED(
        HttpStatus.TOO_MANY_REQUESTS,
        "PHONE_VERIFICATION_DAILY_LIMIT_EXCEEDED",
        "오늘 인증번호 요청 가능 횟수를 초과했습니다.",
    ),

    SIGNUP_CONFIRMATION_IN_PROGRESS(
        HttpStatus.CONFLICT,
        "SIGNUP_CONFIRMATION_IN_PROGRESS",
        "회원가입 인증 처리가 진행 중입니다. 잠시 후 다시 시도해주세요.",
    ),

    SIGNUP_ALREADY_COMPLETED(
        HttpStatus.CONFLICT,
        "SIGNUP_ALREADY_COMPLETED",
        "다른 기기에서 회원가입이 완료되었습니다. 다시 로그인해주세요.",
    ),

    SMS_CONFIGURATION_REQUIRED(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "SMS_CONFIGURATION_REQUIRED",
        "SMS 발송 설정이 필요합니다.",
    ),

    SMS_SEND_FAILED(
        HttpStatus.BAD_GATEWAY,
        "SMS_SEND_FAILED",
        "SMS 발송에 실패했습니다.",
    )
}
