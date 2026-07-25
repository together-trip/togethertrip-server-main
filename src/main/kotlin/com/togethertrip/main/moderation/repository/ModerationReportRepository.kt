package com.togethertrip.main.moderation.repository

import com.togethertrip.main.moderation.domain.ModerationReport
import com.togethertrip.main.moderation.domain.ModerationReportStatus
import com.togethertrip.main.moderation.domain.ModerationTargetType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ModerationReportRepository :
    JpaRepository<ModerationReport, Long>,
    ModerationReportQueryRepository {
    fun existsByReporterIdAndTripIdAndTargetTypeAndTargetIdAndStatusInAndDeletedAtIsNull(
        reporterId: Long,
        tripId: Long,
        targetType: ModerationTargetType,
        targetId: Long,
        statuses: Collection<ModerationReportStatus>,
    ): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ModerationReport r where r.id = :id and r.deletedAt is null")
    fun findLockedById(@Param("id") id: Long): ModerationReport?
}
