package com.togethertrip.main.exchange.scheduler

import com.togethertrip.main.exchange.config.ExchangeRateProperties
import com.togethertrip.main.exchange.service.ExchangeRateImportService
import com.togethertrip.main.exchange.support.ExchangeRateDistributedLock
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(100)
class ExchangeRateBackfillRunner(
    private val properties: ExchangeRateProperties,
    private val distributedLock: ExchangeRateDistributedLock,
    private val importService: ExchangeRateImportService,
) : CommandLineRunner {

    override fun run(vararg args: String) {
        if (!properties.backfill.enabled) {
            return
        }

        val from = properties.backfill.from
            ?: throw IllegalStateException("exchange-rate.backfill.from 설정이 필요합니다.")
        val to = properties.backfill.to
            ?: throw IllegalStateException("exchange-rate.backfill.to 설정이 필요합니다.")

        require(!from.isAfter(to)) {
            "exchange-rate.backfill.from은 to보다 이후일 수 없습니다."
        }

        distributedLock.runIfAcquired {
            importService.importMissingRates(
                from = from,
                to = to,
                maxDays = properties.backfill.maxDaysPerRun,
                pauseBetweenRequests = properties.backfill.pauseBetweenRequests,
            )
        }
    }
}
