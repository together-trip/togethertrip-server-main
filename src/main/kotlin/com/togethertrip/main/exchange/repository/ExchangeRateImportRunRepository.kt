package com.togethertrip.main.exchange.repository

import com.togethertrip.main.exchange.domain.ExchangeRateImportRun
import com.togethertrip.main.exchange.domain.ExchangeRateImportRunStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ExchangeRateImportRunRepository : JpaRepository<ExchangeRateImportRun, Long> {

    fun existsByProviderAndRateDateAndStatusAndDeletedAtIsNull(
        provider: String,
        rateDate: LocalDate,
        status: ExchangeRateImportRunStatus,
    ): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT run
        FROM ExchangeRateImportRun run
        WHERE run.provider = :provider
          AND run.rateDate = :rateDate
          AND run.deletedAt IS NULL
        """
    )
    fun findActiveForUpdate(
        @Param("provider") provider: String,
        @Param("rateDate") rateDate: LocalDate,
    ): ExchangeRateImportRun?
}
