package com.togethertrip.main.moderation.domain

import com.togethertrip.main.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.Instant

@Entity
@Table(name = "moderation_report_audits")
class ModerationReportAudit(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    val report: ModerationReport,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", nullable = false)
    val actor: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val action: ModerationAction,

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    val previousStatus: ModerationReportStatus?,

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, length = 30)
    val nextStatus: ModerationReportStatus,

    @Column(length = 1000)
    val note: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0L

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set
}
