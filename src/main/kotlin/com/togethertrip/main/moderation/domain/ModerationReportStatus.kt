package com.togethertrip.main.moderation.domain

enum class ModerationReportStatus {
    PENDING, // 운영자가 아직 검토를 시작하지 않은 상태
    IN_REVIEW, // 운영자가 신고 내용을 검토 중인 상태
    RESOLVED, // 신고가 유효하여 필요한 조치를 완료한 상태
    REJECTED, // 신고 사유가 인정되지 않아 반려한 상태
}
