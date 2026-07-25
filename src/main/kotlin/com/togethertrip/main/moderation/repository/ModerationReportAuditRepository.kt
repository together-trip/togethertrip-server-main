package com.togethertrip.main.moderation.repository

import com.togethertrip.main.moderation.domain.ModerationReportAudit
import org.springframework.data.repository.Repository

interface ModerationReportAuditRepository : Repository<ModerationReportAudit, Long> {
    fun <S : ModerationReportAudit> save(entity: S): S
}
