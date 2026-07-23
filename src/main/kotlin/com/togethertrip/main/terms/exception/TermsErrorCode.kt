package com.togethertrip.main.terms.exception

import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class TermsErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    TERM_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "TERM_NOT_FOUND",
        "약관 항목을 찾을 수 없습니다.",
    ),

    REQUIRED_TERM_MISSING(
        HttpStatus.BAD_REQUEST,
        "REQUIRED_TERM_MISSING",
        "필수 약관 동의가 필요합니다.",
    ),

    TERM_VERSION_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "TERM_VERSION_MISMATCH",
        "최신 약관 버전으로 다시 동의해주세요.",
    ),

    REQUIRED_TERM_WITHDRAW_NOT_ALLOWED(
        HttpStatus.BAD_REQUEST,
        "REQUIRED_TERM_WITHDRAW_NOT_ALLOWED",
        "필수 약관은 철회할 수 없습니다.",
    )
}
