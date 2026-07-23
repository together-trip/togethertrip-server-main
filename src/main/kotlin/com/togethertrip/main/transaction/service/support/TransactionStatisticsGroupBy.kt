package com.togethertrip.main.transaction.service.support

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode

enum class TransactionStatisticsGroupBy(
    val value: String,
) {
    TYPE("type"),
    CATEGORY("category"),
    PARTICIPANT("participant"),
    ;

    companion object {
        fun from(groupBy: String?): TransactionStatisticsGroupBy {
            val normalizedGroupBy = groupBy
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
                ?: TYPE.value

            // 빈 groupBy는 type 통계로 처리하고, 지원하지 않는 값은 입력 오류로 본다.
            return entries.firstOrNull { it.value == normalizedGroupBy }
                ?: throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }
}
