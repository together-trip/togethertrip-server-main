package com.togethertrip.main.exchange.scheduler

import com.togethertrip.main.exchange.config.ExchangeRateProperties
import com.togethertrip.main.exchange.service.ExchangeRateImportService
import com.togethertrip.main.exchange.support.ExchangeRateDistributedLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class ExchangeRateScheduler(
    private val properties: ExchangeRateProperties,
    private val distributedLock: ExchangeRateDistributedLock,
    private val importService: ExchangeRateImportService,
    private val clock: Clock,
) {

    @Scheduled(cron = "\${exchange-rate.scheduler.cron:0 30 11 * * *}", zone = "Asia/Seoul")
    fun importToday() {
        if (!properties.scheduler.enabled) {
            return
        }

        distributedLock.runIfAcquired {
            val today = LocalDate.now(clock)
            logger.info(
                "스케줄 환율 수집을 시작합니다. today={} catchUpDays={}",
                today,
                properties.scheduler.catchUpDays,
            )
            importService.importMissingRecentRates(
                today = today,
                catchUpDays = properties.scheduler.catchUpDays,
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExchangeRateScheduler::class.java)
    }
}
