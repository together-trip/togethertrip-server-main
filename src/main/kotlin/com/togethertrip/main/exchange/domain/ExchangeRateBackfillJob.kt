package com.togethertrip.main.exchange.domain

import com.togethertrip.main.global.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "exchange_rate_backfill_jobs")
@SQLRestriction("deleted_at IS NULL")
class ExchangeRateBackfillJob(

    @Column(name = "requested_by", nullable = false)
    var requestedBy: Long,

    @Column(name = "from_date", nullable = false)
    var fromDate: LocalDate,

    @Column(name = "to_date", nullable = false)
    var toDate: LocalDate,

    @Column(name = "pause_between_requests_millis", nullable = false)
    var pauseBetweenRequestsMillis: Long,

    @Column(name = "batch_job_execution_id")
    var batchJobExecutionId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ExchangeRateBackfillJobStatus = ExchangeRateBackfillJobStatus.REQUESTED,

    @Column(name = "total_requested_days", nullable = false)
    var totalRequestedDays: Long = 0,

    @Column(name = "processed_days", nullable = false)
    var processedDays: Int = 0,

    @Column(name = "success_count", nullable = false)
    var successCount: Int = 0,

    @Column(name = "failed_count", nullable = false)
    var failedCount: Int = 0,

    @Column(name = "no_data_count", nullable = false)
    var noDataCount: Int = 0,

    @Column(name = "non_business_day_count", nullable = false)
    var nonBusinessDayCount: Int = 0,

    @Column(name = "last_error_message", length = 500)
    var lastErrorMessage: String? = null,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "finished_at")
    var finishedAt: Instant? = null,

) : BaseEntity()
