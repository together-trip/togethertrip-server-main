package com.togethertrip.main.exchange.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate

@Component
@ConfigurationProperties(prefix = "exchange-rate")
class ExchangeRateProperties {
    var koreaExim: KoreaExim = KoreaExim()
    var importValidation: ImportValidation = ImportValidation()
    var scheduler: Scheduler = Scheduler()
    var backfill: Backfill = Backfill()
    var lock: Lock = Lock()

    class KoreaExim {
        var baseUrl: String = "https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON"
        var authKey: String = ""
        var dataCode: String = "AP01"
        var timeout: Duration = Duration.ofSeconds(10)
    }

    class ImportValidation {
        var minimumRowCount: Int = 20
        var requiredCurrencies: List<String> = listOf("USD", "JPY", "EUR")
    }

    class Scheduler {
        var enabled: Boolean = false
        var cron: String = "0 30 11 * * *"
        var catchUpDays: Long = 7
    }

    class Backfill {
        var enabled: Boolean = false
        var from: LocalDate? = null
        var to: LocalDate? = null
        var maxDaysPerRun: Long = 31
        var pauseBetweenRequests: Duration = Duration.ofMillis(300)
    }

    class Lock {
        var name: String = "exchange-rate:import:korea-exim"
        var waitTime: Duration = Duration.ZERO
        var leaseTime: Duration = Duration.ofMinutes(10)
    }
}
