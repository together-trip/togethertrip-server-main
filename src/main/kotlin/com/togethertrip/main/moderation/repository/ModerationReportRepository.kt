package com.togethertrip.main.moderation.repository

import com.togethertrip.main.moderation.domain.ModerationReport
import com.togethertrip.main.moderation.domain.ModerationReportStatus
import com.togethertrip.main.moderation.domain.ModerationTargetType
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ModerationReportRepository : JpaRepository<ModerationReport, Long> {
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

    @Query(
        """
        SELECT r
        FROM ModerationReport r
        WHERE r.deletedAt IS NULL
          AND (:statusFilterEnabled = false OR r.status = :status)
          AND (:targetTypeFilterEnabled = false OR r.targetType = :targetType)
          AND (
            :cursorFilterEnabled = false
            OR r.createdAt > :cursorCreatedAt
            OR (r.createdAt = :cursorCreatedAt AND r.id > :cursorId)
          )
        ORDER BY r.createdAt ASC, r.id ASC
        """
    )
    fun findReports(
        @Param("status") status: ModerationReportStatus?,
        @Param("statusFilterEnabled") statusFilterEnabled: Boolean,
        @Param("targetType") targetType: ModerationTargetType?,
        @Param("targetTypeFilterEnabled") targetTypeFilterEnabled: Boolean,
        @Param("cursorCreatedAt") cursorCreatedAt: java.time.Instant?,
        @Param("cursorId") cursorId: Long?,
        @Param("cursorFilterEnabled") cursorFilterEnabled: Boolean,
        pageable: Pageable,
    ): List<ModerationReport>
}
