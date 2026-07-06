package com.togethertrip.main.transaction.service.support

import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionEvent
import com.togethertrip.main.transaction.domain.TransactionEventType
import com.togethertrip.main.transaction.domain.event.TransactionEventPayload
import com.togethertrip.main.transaction.repository.TransactionEventRepository
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.user.domain.User
import org.springframework.stereotype.Component

@Component
class TransactionEventRecorder(
    private val transactionEventRepository: TransactionEventRepository,
) {

    fun record(
        trip: Trip,
        transaction: Transaction,
        eventType: TransactionEventType,
        createdBy: User,
    ): TransactionEvent {
        val aggregateVersion = trip.advanceExpenseVersion()

        val payload = TransactionEventPayload.from(
            transaction = transaction,
            eventType = eventType,
        ).toJson()
        val event = TransactionEvent(
            transaction = transaction,
            trip = trip,
            eventType = eventType,
            aggregateVersion = aggregateVersion,
            payload = payload,
            createdBy = createdBy,
        )

        return transactionEventRepository.save(event)
    }
}
