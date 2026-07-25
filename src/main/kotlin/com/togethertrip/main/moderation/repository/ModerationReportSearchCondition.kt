package com.togethertrip.main.moderation.repository

import com.togethertrip.main.moderation.domain.ModerationReportStatus
import com.togethertrip.main.moderation.domain.ModerationTargetType
import com.togethertrip.main.moderation.pagination.ModerationReportCursor

data class ModerationReportSearchCondition(
    val status: ModerationReportStatus?,
    val targetType: ModerationTargetType?,
    val cursor: ModerationReportCursor?,
)
