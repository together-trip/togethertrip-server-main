package com.togethertrip.main.moderation.exception

import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ModerationErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "MODERATION_TARGET_NOT_FOUND", "신고 대상을 찾을 수 없습니다."),
    SELF_REPORT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SELF_REPORT_NOT_ALLOWED", "자신을 신고할 수 없습니다."),
    DUPLICATE_REPORT(HttpStatus.CONFLICT, "DUPLICATE_REPORT", "이미 처리 중인 신고가 있습니다."),
    SELF_BLOCK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SELF_BLOCK_NOT_ALLOWED", "자신을 차단할 수 없습니다."),
    BLOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "BLOCK_NOT_FOUND", "차단 정보를 찾을 수 없습니다."),
    INTERACTION_BLOCKED(HttpStatus.FORBIDDEN, "INTERACTION_BLOCKED", "차단 관계에서는 상호작용할 수 없습니다."),
    USER_RESTRICTED(HttpStatus.FORBIDDEN, "USER_RESTRICTED", "운영 정책에 따라 활동이 제한된 사용자입니다."),
    CONTENT_REJECTED(HttpStatus.BAD_REQUEST, "CONTENT_REJECTED", "운영 정책에 맞지 않는 내용이 포함되어 있습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "MODERATION_REPORT_NOT_FOUND", "신고를 찾을 수 없습니다."),
    INVALID_ACTION(HttpStatus.BAD_REQUEST, "INVALID_MODERATION_ACTION", "신고 대상에 적용할 수 없는 조치입니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "INVALID_MODERATION_STATUS_TRANSITION", "허용되지 않은 신고 상태 변경입니다."),
}
