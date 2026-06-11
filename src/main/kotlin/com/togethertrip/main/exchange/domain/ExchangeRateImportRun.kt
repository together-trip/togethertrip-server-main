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
@Table(name = "exchange_rate_import_runs")
@SQLRestriction("deleted_at IS NULL")
class ExchangeRateImportRun(

    @Column(nullable = false, length = 50)
    var provider: String,

    @Column(name = "rate_date", nullable = false)
    var rateDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ExchangeRateImportRunStatus = ExchangeRateImportRunStatus.PENDING,

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "row_count", nullable = false)
    var rowCount: Int = 0,

    @Column(name = "upsert_count", nullable = false)
    var upsertCount: Int = 0,

    @Column(name = "last_result_code", length = 20)
    var lastResultCode: String? = null,

    @Column(name = "last_error_message", length = 500)
    var lastErrorMessage: String? = null,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "finished_at")
    var finishedAt: Instant? = null,

) : BaseEntity()
