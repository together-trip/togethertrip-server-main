package com.togethertrip.main.user.exception

import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class UserErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    USER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "USER_NOT_FOUND",
        "사용자를 찾을 수 없습니다.",
    ),

    INACTIVE_USER(
        HttpStatus.FORBIDDEN,
        "INACTIVE_USER",
        "활성 상태의 사용자가 아닙니다.",
    ),

    NICKNAME_ALREADY_USED(
        HttpStatus.CONFLICT,
        "NICKNAME_ALREADY_USED",
        "이미 사용 중인 닉네임입니다.",
    )
}
