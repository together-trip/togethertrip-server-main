package com.togethertrip.main.settlement.domain.calculation

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettlementCalculatorTest {

    private val calculator = SettlementCalculator()

    @Test
    fun `2인 정산 송금 목록을 계산한다`() {
        val result = calculator.calculate(
            SettlementCalculationInput(
                baseCurrency = "KRW",
                payments = listOf(
                    SettlementPaymentInput(
                        participantId = 1L,
                        amount = BigDecimal("10000.00"),
                    )
                ),
                shares = listOf(
                    SettlementShareInput(
                        participantId = 1L,
                        amount = BigDecimal("5000.00"),
                    ),
                    SettlementShareInput(
                        participantId = 2L,
                        amount = BigDecimal("5000.00"),
                    )
                ),
            )
        )

        assertEquals(BigDecimal("10000.00"), result.totalExpenseAmount)
        assertEquals(BigDecimal("10000.00"), result.totalShareAmount)
        assertEquals(BigDecimal("5000.00"), result.balances.first { it.participantId == 1L }.netAmount)
        assertEquals(BigDecimal("-5000.00"), result.balances.first { it.participantId == 2L }.netAmount)
        assertEquals(
            SettlementTransferPlan(
                senderParticipantId = 2L,
                receiverParticipantId = 1L,
                amount = BigDecimal("5000.00"),
            ),
            result.transfers.single(),
        )
    }

    @Test
    fun `3인 이상 정산은 필요한 송금 목록만 만든다`() {
        val result = calculator.calculate(
            SettlementCalculationInput(
                baseCurrency = "KRW",
                payments = listOf(
                    SettlementPaymentInput(1L, BigDecimal("30000.00")),
                    SettlementPaymentInput(2L, BigDecimal("10000.00")),
                ),
                shares = listOf(
                    SettlementShareInput(1L, BigDecimal("10000.00")),
                    SettlementShareInput(2L, BigDecimal("10000.00")),
                    SettlementShareInput(3L, BigDecimal("10000.00")),
                    SettlementShareInput(4L, BigDecimal("10000.00")),
                ),
            )
        )

        assertEquals(BigDecimal("20000.00"), result.balances.first { it.participantId == 1L }.netAmount)
        assertEquals(BigDecimal("0.00"), result.balances.first { it.participantId == 2L }.netAmount)
        assertEquals(BigDecimal("-10000.00"), result.balances.first { it.participantId == 3L }.netAmount)
        assertEquals(BigDecimal("-10000.00"), result.balances.first { it.participantId == 4L }.netAmount)
        assertEquals(
            listOf(
                SettlementTransferPlan(3L, 1L, BigDecimal("10000.00")),
                SettlementTransferPlan(4L, 1L, BigDecimal("10000.00")),
            ),
            result.transfers,
        )
    }

    @Test
    fun `다중 결제자와 다중 부담자를 참여자별로 집계한다`() {
        val result = calculator.calculate(
            SettlementCalculationInput(
                baseCurrency = "KRW",
                payments = listOf(
                    SettlementPaymentInput(1L, BigDecimal("3000.00")),
                    SettlementPaymentInput(1L, BigDecimal("2000.00")),
                    SettlementPaymentInput(2L, BigDecimal("5000.00")),
                ),
                shares = listOf(
                    SettlementShareInput(1L, BigDecimal("2500.00")),
                    SettlementShareInput(2L, BigDecimal("2500.00")),
                    SettlementShareInput(3L, BigDecimal("5000.00")),
                ),
            )
        )

        assertEquals(BigDecimal("2500.00"), result.balances.first { it.participantId == 1L }.netAmount)
        assertEquals(BigDecimal("2500.00"), result.balances.first { it.participantId == 2L }.netAmount)
        assertEquals(BigDecimal("-5000.00"), result.balances.first { it.participantId == 3L }.netAmount)
        assertEquals(BigDecimal("0.00"), result.balances.sumOf { it.netAmount })
    }

    @Test
    fun `정산할 순잔액이 없으면 송금 목록을 만들지 않는다`() {
        val result = calculator.calculate(
            SettlementCalculationInput(
                baseCurrency = "KRW",
                payments = listOf(
                    SettlementPaymentInput(1L, BigDecimal("5000.00")),
                    SettlementPaymentInput(2L, BigDecimal("5000.00")),
                ),
                shares = listOf(
                    SettlementShareInput(1L, BigDecimal("5000.00")),
                    SettlementShareInput(2L, BigDecimal("5000.00")),
                ),
            )
        )

        assertTrue(result.transfers.isEmpty())
    }
}
