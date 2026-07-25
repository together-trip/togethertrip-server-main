package com.togethertrip.main.moderation.domain

enum class ModerationAction {
    NONE, // 신고 상태만 변경하고 대상에는 조치하지 않음
    HIDE, // 대상 콘텐츠를 사용자에게 노출하지 않음
    DELETE, // 대상 콘텐츠를 소프트 삭제함
    RESTRICT_USER, // 대상 사용자의 서비스 활동을 제한함
    UNRESTRICT_USER, // 대상 사용자의 활동 제한을 해제함
}
