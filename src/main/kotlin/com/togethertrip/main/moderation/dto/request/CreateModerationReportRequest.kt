package com.togethertrip.main.moderation.dto.request

import com.togethertrip.main.moderation.domain.ModerationReportReason
import com.togethertrip.main.moderation.domain.ModerationTargetType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreateModerationReportRequest(
    @field:NotNull
    val targetType: ModerationTargetType?,

    @field:NotNull
    @field:Positive
    val targetId: Long?,

    @field:NotNull
    val reason: ModerationReportReason?,

    @field:Size(max = 1000)
    val description: String? = null,
)
