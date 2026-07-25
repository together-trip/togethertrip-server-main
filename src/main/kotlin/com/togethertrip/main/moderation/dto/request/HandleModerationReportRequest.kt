package com.togethertrip.main.moderation.dto.request

import com.togethertrip.main.moderation.domain.ModerationAction
import com.togethertrip.main.moderation.domain.ModerationReportStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class HandleModerationReportRequest(
    @field:NotNull
    val status: ModerationReportStatus?,
    @field:NotNull
    val action: ModerationAction?,
    @field:Size(max = 1000)
    val note: String? = null,
    @field:Min(1)
    @field:Max(365)
    val restrictionDays: Long? = null,
)
