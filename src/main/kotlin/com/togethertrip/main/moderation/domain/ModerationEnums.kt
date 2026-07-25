package com.togethertrip.main.moderation.domain

enum class ModerationTargetType {
    POST,
    COMMENT,
    USER,
    TRIP_RECAP,
}

enum class ModerationReportReason {
    SPAM,
    HARASSMENT,
    HATE_SPEECH,
    SEXUAL_CONTENT,
    VIOLENCE,
    PRIVACY,
    OTHER,
}

enum class ModerationReportStatus {
    PENDING,
    IN_REVIEW,
    RESOLVED,
    REJECTED,
}

enum class ModerationAction {
    NONE,
    HIDE,
    DELETE,
    RESTRICT_USER,
    UNRESTRICT_USER,
}
