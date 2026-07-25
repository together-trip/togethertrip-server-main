package com.togethertrip.main.moderation.domain

enum class ModerationTargetType {
    POST, // 기록 게시글 또는 소비 게시글
    COMMENT, // 게시글에 작성된 댓글 또는 대댓글
    USER, // 여행에 참여 중인 사용자
    TRIP_RECAP, // 여행 종료 후 생성된 여행 요약
}
