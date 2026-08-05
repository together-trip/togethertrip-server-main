package com.togethertrip.main.moderation.domain

enum class ModerationReportReason {
    SPAM, // 반복 게시 또는 광고성 콘텐츠
    HARASSMENT, // 괴롭힘 또는 모욕적인 행위
    HATE_SPEECH, // 특정 집단을 향한 혐오 표현
    SEXUAL_CONTENT, // 음란하거나 성적인 콘텐츠
    VIOLENCE, // 폭력적이거나 위협적인 콘텐츠
    PRIVACY, // 개인정보 노출 또는 사생활 침해
    OTHER, // 사전에 분류되지 않은 기타 사유
}
