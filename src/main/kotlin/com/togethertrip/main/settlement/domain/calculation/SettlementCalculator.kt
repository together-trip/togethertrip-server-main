package com.togethertrip.main.settlement.domain.calculation

import java.math.BigDecimal
import java.math.RoundingMode

class SettlementCalculator {

    fun calculate(input: SettlementCalculationInput): SettlementCalculationResult {
        val paidAmounts = input.payments
            .groupBy(SettlementPaymentInput::participantId)
            .mapValues { (_, payments) -> payments.sumOfAmount(SettlementPaymentInput::amount) }
        val shareAmounts = input.shares
            .groupBy(SettlementShareInput::participantId)
            .mapValues { (_, shares) -> shares.sumOfAmount(SettlementShareInput::amount) }
        val participantIds = (paidAmounts.keys + shareAmounts.keys).sorted()
        val balances = participantIds.map { participantId ->
            val paidAmount = paidAmounts[participantId] ?: ZERO_AMOUNT
            val shareAmount = shareAmounts[participantId] ?: ZERO_AMOUNT
            SettlementParticipantBalance(
                participantId = participantId,
                paidAmount = paidAmount.toMoney(),
                shareAmount = shareAmount.toMoney(),
                netAmount = paidAmount.subtract(shareAmount).toMoney(),
            )
        }
        val totalExpenseAmount = input.payments.sumOfAmount(SettlementPaymentInput::amount).toMoney()
        val totalShareAmount = input.shares.sumOfAmount(SettlementShareInput::amount).toMoney()

        return SettlementCalculationResult(
            baseCurrency = input.baseCurrency,
            totalExpenseAmount = totalExpenseAmount,
            totalShareAmount = totalShareAmount,
            balances = balances,
            transfers = createTransferPlans(balances),
        )
    }

    private fun createTransferPlans(
        balances: List<SettlementParticipantBalance>,
    ): List<SettlementTransferPlan> {
        val debtors = balances
            .filter { it.netAmount < ZERO_AMOUNT }
            .map { BalanceCursor(it.participantId, it.netAmount.abs()) }
            .toMutableList()
        val creditors = balances
            .filter { it.netAmount > ZERO_AMOUNT }
            .map { BalanceCursor(it.participantId, it.netAmount) }
            .toMutableList()
        val transfers = mutableListOf<SettlementTransferPlan>()
        var debtorIndex = 0
        var creditorIndex = 0

        while (debtorIndex < debtors.size && creditorIndex < creditors.size) {
            val debtor = debtors[debtorIndex]
            val creditor = creditors[creditorIndex]
            val amount = debtor.remaining.min(creditor.remaining).toMoney()

            if (amount > ZERO_AMOUNT) {
                transfers.add(
                    SettlementTransferPlan(
                        senderParticipantId = debtor.participantId,
                        receiverParticipantId = creditor.participantId,
                        amount = amount,
                    )
                )
            }

            debtor.remaining = debtor.remaining.subtract(amount).toMoney()
            creditor.remaining = creditor.remaining.subtract(amount).toMoney()

            if (debtor.remaining <= ZERO_AMOUNT) {
                debtorIndex += 1
            }
            if (creditor.remaining <= ZERO_AMOUNT) {
                creditorIndex += 1
            }
        }

        return transfers
    }

    private data class BalanceCursor(
        val participantId: Long,
        var remaining: BigDecimal,
    )

    private fun <T> List<T>.sumOfAmount(
        selector: (T) -> BigDecimal,
    ): BigDecimal {
        return fold(ZERO_AMOUNT) { acc, input -> acc.add(selector(input)) }
    }

    private fun BigDecimal.toMoney(): BigDecimal {
        return setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    }

    private fun BigDecimal.min(other: BigDecimal): BigDecimal {
        return if (this <= other) this else other
    }

    private companion object {
        private const val MONEY_SCALE = 2
        private val ZERO_AMOUNT = BigDecimal.ZERO.setScale(MONEY_SCALE)
    }
}
