package com.togethertrip.main.transaction.service.support

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TransactionStatisticsPeriod(
    val from: LocalDate?,
    val to: LocalDate?,
) {
    val fromInstant: Instant? = from?.atStartOfDay(STATISTICS_ZONE_ID)?.toInstant()
    val toExclusiveInstant: Instant? = to?.plusDays(1)?.atStartOfDay(STATISTICS_ZONE_ID)?.toInstant()

    companion object {
        private val STATISTICS_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")

        fun from(
            from: String?,
            to: String?,
        ): TransactionStatisticsPeriod {
            val fromDate = parseDate(from)
            val toDate = parseDate(to)

            // 시작일이 종료일보다 늦으면 기간 필터를 적용할 수 없다.
            if (fromDate != null && toDate != null && fromDate > toDate) {
                throw BusinessException(CommonErrorCode.INVALID_INPUT)
            }

            return TransactionStatisticsPeriod(
                from = fromDate,
                to = toDate,
            )
        }

        private fun parseDate(value: String?): LocalDate? {
            val trimmedValue = value
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return null

            // 날짜 파라미터는 yyyy-MM-dd 형식만 허용한다.
            return try {
                LocalDate.parse(trimmedValue)
            } catch (_: DateTimeException) {
                throw BusinessException(CommonErrorCode.INVALID_INPUT)
            }
        }
    }
}
