package com.togethertrip.main.moderation.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
@Table(name = "moderation_reports")
@SQLRestriction("deleted_at IS NULL")
class ModerationReport(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    val reporter: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    val targetType: ModerationTargetType,

    @Column(name = "target_id", nullable = false)
    val targetId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    val targetUser: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val reason: ModerationReportReason,

    @Column(length = 1000)
    val description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ModerationReportStatus = ModerationReportStatus.PENDING,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_user_id")
    var handledBy: User? = null,

    @Column(name = "handled_at")
    var handledAt: Instant? = null,

    @Version
    @Column(nullable = false)
    var version: Long = 0L,
) : BaseEntity() {
    fun handle(status: ModerationReportStatus, admin: User, now: Instant) {
        this.status = status
        handledBy = admin
        handledAt = if (status == ModerationReportStatus.RESOLVED || status == ModerationReportStatus.REJECTED) now else null
        updatedAt = now
    }
}
