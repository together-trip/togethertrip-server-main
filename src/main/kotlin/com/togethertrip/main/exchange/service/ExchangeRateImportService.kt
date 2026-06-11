package com.togethertrip.main.exchange.service

import com.togethertrip.main.exchange.client.KoreaEximExchangeRateClient
import com.togethertrip.main.exchange.client.KoreaEximExchangeRateFetchResult
import com.togethertrip.main.exchange.config.ExchangeRateProperties
import com.togethertrip.main.exchange.domain.ExchangeRateImportRow
import com.togethertrip.main.exchange.repository.ExchangeRateUpsertRepository
import com.togethertrip.main.exchange.service.normalizer.KoreaEximExchangeRateNormalizer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

@Service
class ExchangeRateImportService(
    private val client: KoreaEximExchangeRateClient,
    private val normalizer: KoreaEximExchangeRateNormalizer,
    private val properties: ExchangeRateProperties,
    private val importRunService: ExchangeRateImportRunService,
    private val upsertRepository: ExchangeRateUpsertRepository,
) {

    fun importMissingRecentRates(
        today: LocalDate,
        catchUpDays: Long,
    ): List<ExchangeRateImportResult> {
        val startDate = today.minusDays(catchUpDays.coerceAtLeast(0))

        return importMissingRates(
            from = startDate,
            to = today,
            pauseBetweenRequests = Duration.ZERO,
        )
    }

    fun importMissingRates(
        from: LocalDate,
        to: LocalDate,
        pauseBetweenRequests: Duration,
    ): List<ExchangeRateImportResult> {
        val targetDates = findMissingRateDates(from, to)

        return targetDates.mapIndexed { index, rateDate ->
            if (index > 0 && !pauseBetweenRequests.isZero && !pauseBetweenRequests.isNegative) {
                Thread.sleep(pauseBetweenRequests.toMillis())
            }

            logger.info("누락 환율 수집을 시작합니다. rateDate={}", rateDate)
            importByDate(rateDate)
        }
    }

    fun findMissingRateDates(
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDate> {
        require(!from.isAfter(to)) {
            "from은 to보다 이후일 수 없습니다."
        }

        return generateSequence(from) { date -> date.plusDays(1) }
            .takeWhile { date -> !date.isAfter(to) }
            .filter { rateDate -> !importRunService.hasCompleted(rateDate) }
            .toList()
    }

    fun importByDate(rateDate: LocalDate): ExchangeRateImportResult {
        if (isSkippedNonBusinessDay(rateDate)) {
            val result = ExchangeRateImportResult.NonBusinessDay(rateDate)
            logger.info("환율 수집 대상 영업일이 아니어서 API 호출을 건너뜁니다. rateDate={}", rateDate)
            importRunService.finish(result)
            return result
        }

        importRunService.start(rateDate)

        val result = try {
            when (val fetchResult = client.fetchRates(rateDate)) {
                is KoreaEximExchangeRateFetchResult.Success -> importSuccess(rateDate, fetchResult)
                KoreaEximExchangeRateFetchResult.NoData -> {
                    logger.info("한국수출입은행 환율 데이터가 없습니다. rateDate={}", rateDate)
                    ExchangeRateImportResult.NoData(rateDate)
                }
                is KoreaEximExchangeRateFetchResult.Failed -> {
                    logger.warn(
                        "한국수출입은행 환율 API 실패 응답입니다. rateDate={} resultCode={} reason={}",
                        rateDate,
                        fetchResult.resultCode.code,
                        fetchResult.resultCode.description,
                    )
                    ExchangeRateImportResult.Failed(rateDate, fetchResult.resultCode)
                }
            }
        } catch (exception: Exception) {
            logger.warn(
                "한국수출입은행 환율 수집 중 예외가 발생했습니다. rateDate={} message={}",
                rateDate,
                exception.message,
                exception,
            )
            ExchangeRateImportResult.Error(
                rateDate = rateDate,
                message = exception.message ?: exception::class.simpleName.orEmpty(),
            )
        }

        importRunService.finish(result)
        return result
    }

    private fun isSkippedNonBusinessDay(rateDate: LocalDate): Boolean {
        if (!properties.importValidation.skipWeekends) {
            return false
        }

        return rateDate.dayOfWeek == DayOfWeek.SATURDAY ||
            rateDate.dayOfWeek == DayOfWeek.SUNDAY
    }

    private fun importSuccess(
        rateDate: LocalDate,
        result: KoreaEximExchangeRateFetchResult.Success,
    ): ExchangeRateImportResult.Imported {
        val rows = result.responses
            .filter { it.currencyUnit != null && it.dealBaseRate != null }
            .map { normalizer.normalize(it, rateDate) }

        validateRows(rateDate, rows)

        val upsertCount = upsertRepository.upsertAll(rows)

        logger.info(
            "한국수출입은행 환율 데이터를 저장했습니다. rateDate={} rows={} upsertCount={}",
            rateDate,
            rows.size,
            upsertCount,
        )

        return ExchangeRateImportResult.Imported(
            rateDate = rateDate,
            rowCount = rows.size,
            upsertCount = upsertCount,
        )
    }

    private fun validateRows(
        rateDate: LocalDate,
        rows: List<ExchangeRateImportRow>,
    ) {
        if (rows.size < properties.importValidation.minimumRowCount) {
            throw IllegalStateException(
                "환율 row 수가 최소 기준보다 적습니다. rateDate=$rateDate rows=${rows.size} " +
                    "minimum=${properties.importValidation.minimumRowCount}"
            )
        }

        val targetCurrencies = rows.map { it.targetCurrency.uppercase() }.toSet()
        val missingCurrencies = properties.importValidation.requiredCurrencies
            .map { it.uppercase() }
            .filter { it !in targetCurrencies }

        if (missingCurrencies.isNotEmpty()) {
            throw IllegalStateException(
                "필수 환율 통화가 누락되었습니다. rateDate=$rateDate missingCurrencies=$missingCurrencies"
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExchangeRateImportService::class.java)
    }
}
