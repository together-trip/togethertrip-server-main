package com.togethertrip.main.settlement.domain

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SettlementTransferDirectionTest {

    @Test
    fun `송금 방향 별칭을 파싱한다`() {
        assertEquals(SettlementTransferDirection.SENT, SettlementTransferDirection.parse("SENT"))
        assertEquals(SettlementTransferDirection.SENT, SettlementTransferDirection.parse("send"))
        assertEquals(SettlementTransferDirection.SENT, SettlementTransferDirection.parse(" sender "))
        assertEquals(SettlementTransferDirection.RECEIVED, SettlementTransferDirection.parse("RECEIVED"))
        assertEquals(SettlementTransferDirection.RECEIVED, SettlementTransferDirection.parse("receive"))
        assertEquals(SettlementTransferDirection.RECEIVED, SettlementTransferDirection.parse(" receiver "))
    }

    @Test
    fun `송금 방향이 없으면 null을 반환한다`() {
        assertEquals(null, SettlementTransferDirection.parse(null))
    }

    @Test
    fun `잘못된 송금 방향은 비즈니스 예외로 실패한다`() {
        val exception = assertBusinessException {
            SettlementTransferDirection.parse("UNKNOWN")
        }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_TRANSFER_DIRECTION, exception.errorCode)
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try {
            block()
            throw AssertionError("BusinessException이 발생해야 합니다.")
        } catch (exception: BusinessException) {
            exception
        }
    }
}
