package com.togethertrip.main.exchange.repository

import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJob
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ExchangeRateBackfillJobRepository : JpaRepository<ExchangeRateBackfillJob, Long> {

    fun findByDeletedAtIsNullOrderByCreatedAtDesc(pageable: Pageable): List<ExchangeRateBackfillJob>
}
