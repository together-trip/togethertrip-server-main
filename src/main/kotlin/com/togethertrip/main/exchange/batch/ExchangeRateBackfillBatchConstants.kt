package com.togethertrip.main.exchange.batch

object ExchangeRateBackfillBatchConstants {
    const val JOB_NAME = "exchangeRateBackfillJob"
    const val STEP_NAME = "exchangeRateBackfillStep"
    const val PARAM_BACKFILL_JOB_ID = "backfillJobId"
    const val PARAM_FROM = "from"
    const val PARAM_TO = "to"
    const val PARAM_PAUSE_MILLIS = "pauseMillis"
    const val PARAM_REQUESTED_AT = "requestedAt"
}
