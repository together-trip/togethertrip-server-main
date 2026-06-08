package com.togethertrip.main.settlement.domain

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.exception.SettlementErrorCode

enum class SettlementTransferDirection {
    SENT,
    RECEIVED;

    companion object {
        fun parse(value: String?): SettlementTransferDirection? {
            return when (value?.trim()?.uppercase()) {
                null -> null
                "SENT", "SEND", "SENDER" -> SENT
                "RECEIVED", "RECEIVE", "RECEIVER" -> RECEIVED
                else -> throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_TRANSFER_DIRECTION)
            }
        }
    }
}
