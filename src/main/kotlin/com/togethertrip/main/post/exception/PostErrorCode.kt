package com.togethertrip.main.post.exception

import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class PostErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    POST_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "POST_NOT_FOUND",
        "게시글을 찾을 수 없습니다.",
    ),

    POST_COMMENT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "POST_COMMENT_NOT_FOUND",
        "댓글을 찾을 수 없습니다.",
    ),

    TRANSACTION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "TRANSACTION_NOT_FOUND",
        "거래를 찾을 수 없습니다.",
    ),

    TRANSACTION_TRIP_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "TRANSACTION_TRIP_MISMATCH",
        "거래가 해당 여행에 속하지 않습니다.",
    ),

    POST_LOCKED_BY_SETTLEMENT(
        HttpStatus.CONFLICT,
        "POST_LOCKED_BY_SETTLEMENT",
        "정산이 완료된 여행에서는 소비 기록을 변경할 수 없습니다.",
    )
}
