package com.togethertrip.main.moderation.repository

import com.togethertrip.main.moderation.domain.ModerationReport

interface ModerationReportQueryRepository {
    fun findReports(
        condition: ModerationReportSearchCondition,
        limit: Int,
    ): List<ModerationReport>
}
