package com.togethertrip.main.exchange.dto.response

import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJob
import com.togethertrip.main.exchange.domain.ExchangeRateBackfillJobStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ExchangeRateBackfillJobResponseTest {

    @Test
    fun `처리 대상이 0건이면 진행률은 0퍼센트다`() {
        val job = backfillJob(
            totalRequestedDays = 0,
            processedDays = 0,
        )

        val response = ExchangeRateBackfillJobResponse.from(job)

        assertEquals(BigDecimal("0.00"), response.progressPercent)
    }

    @Test
    fun `요청 일수와 실제 처리 대상 일수를 분리해 제공한다`() {
        val job = backfillJob(
            totalRequestedDays = 3,
        )

        val response = ExchangeRateBackfillJobResponse.from(job)

        assertEquals(10, response.requestedDays)
        assertEquals(3, response.targetDays)
    }

    @Test
    fun `진행률은 처리 건수와 전체 대상 건수로 계산한다`() {
        val job = backfillJob(
            totalRequestedDays = 3,
            processedDays = 2,
        )

        val response = ExchangeRateBackfillJobResponse.from(job)

        assertEquals(BigDecimal("66.67"), response.progressPercent)
    }

    @Test
    fun `진행률은 100퍼센트를 넘지 않는다`() {
        val job = backfillJob(
            totalRequestedDays = 3,
            processedDays = 4,
        )

        val response = ExchangeRateBackfillJobResponse.from(job)

        assertEquals(BigDecimal("100.00"), response.progressPercent)
    }

    @Test
    fun `성공 실패 데이터없음 비영업일 집계는 그대로 제공한다`() {
        val job = backfillJob(
            successCount = 1,
            failedCount = 2,
            noDataCount = 3,
            nonBusinessDayCount = 4,
        )

        val response = ExchangeRateBackfillJobResponse.from(job)

        assertEquals(1, response.successCount)
        assertEquals(2, response.failedCount)
        assertEquals(3, response.noDataCount)
        assertEquals(4, response.nonBusinessDayCount)
    }

    private fun backfillJob(
        totalRequestedDays: Long = 10,
        processedDays: Int = 0,
        successCount: Int = 0,
        failedCount: Int = 0,
        noDataCount: Int = 0,
        nonBusinessDayCount: Int = 0,
    ): ExchangeRateBackfillJob {
        return ExchangeRateBackfillJob(
            requestedBy = 1L,
            fromDate = LocalDate.parse("2026-06-01"),
            toDate = LocalDate.parse("2026-06-10"),
            pauseBetweenRequestsMillis = 300L,
            status = ExchangeRateBackfillJobStatus.COMPLETED,
            totalRequestedDays = totalRequestedDays,
            processedDays = processedDays,
            successCount = successCount,
            failedCount = failedCount,
            noDataCount = noDataCount,
            nonBusinessDayCount = nonBusinessDayCount,
        ).also {
            it.id = 10L
            it.createdAt = Instant.parse("2026-06-11T00:00:00Z")
            it.updatedAt = Instant.parse("2026-06-11T00:00:00Z")
        }
    }
}
