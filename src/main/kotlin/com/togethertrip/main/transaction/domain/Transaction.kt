package com.togethertrip.main.transaction.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.transaction.domain.exchange.TransactionCurrencySnapshot
import com.togethertrip.main.transaction.domain.ledger.TransactionLedgerEntry
import com.togethertrip.main.transaction.exception.TransactionErrorCode
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "transactions")
@SQLRestriction("deleted_at IS NULL")
class Transaction(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    // DDL 기준: created_by_user_id 는 users(id) 를 참조한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    var createdBy: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    var transactionType: TransactionType,

    @Column(nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,

    @Column(nullable = false, length = 3)
    var currency: String,

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6)
    var exchangeRate: BigDecimal,

    @Column(name = "base_currency", nullable = false, length = 3)
    var baseCurrency: String,

    @Column(name = "base_amount", nullable = false, precision = 19, scale = 2)
    var baseAmount: BigDecimal,

    @Column(length = 30)
    var category: String? = null,

    @Column(name = "occurred_at")
    var occurredAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TransactionStatus = TransactionStatus.ACTIVE,

) : BaseEntity() {

    init {
        category = normalizeCategory(category)
    }

    // 거래 낙관적 락 버전 (DDL: version BIGINT NOT NULL DEFAULT 0)
    @Version
    @Column(nullable = false)
    var version: Long = 0

    fun updateSnapshot(
        ledgerEntry: TransactionLedgerEntry,
        currencySnapshot: TransactionCurrencySnapshot,
        category: String?,
        occurredAt: Instant?,
    ) {
        this.transactionType = ledgerEntry.transactionType
        this.amount = ledgerEntry.amount
        this.currency = currencySnapshot.currency
        this.exchangeRate = currencySnapshot.exchangeRate
        this.baseCurrency = currencySnapshot.baseCurrency
        this.baseAmount = currencySnapshot.convert(ledgerEntry.amount)
        updateMetadata(
            category = category,
            occurredAt = occurredAt,
        )
    }

    fun updateMetadata(
        category: String?,
        occurredAt: Instant?,
    ) {
        this.category = normalizeCategory(category)
        this.occurredAt = occurredAt
    }

    fun void() {
        status = TransactionStatus.VOIDED
    }

    fun assertMutableBy(userId: Long) {
        if (createdBy.id != userId) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
        if (status != TransactionStatus.ACTIVE) {
            throw BusinessException(TransactionErrorCode.TRANSACTION_ALREADY_VOIDED)
        }
    }

    fun voidBy(userId: Long) {
        assertMutableBy(userId)
        void()
    }

    private fun normalizeCategory(category: String?): String? {
        return category?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        fun create(
            trip: Trip,
            createdBy: User,
            ledgerEntry: TransactionLedgerEntry,
            currencySnapshot: TransactionCurrencySnapshot,
            category: String?,
            occurredAt: Instant?,
        ): Transaction {
            return Transaction(
                trip = trip,
                createdBy = createdBy,
                transactionType = ledgerEntry.transactionType,
                amount = ledgerEntry.amount,
                currency = currencySnapshot.currency,
                exchangeRate = currencySnapshot.exchangeRate,
                baseCurrency = currencySnapshot.baseCurrency,
                baseAmount = currencySnapshot.convert(ledgerEntry.amount),
                category = category,
                occurredAt = occurredAt,
            )
        }
    }
}
