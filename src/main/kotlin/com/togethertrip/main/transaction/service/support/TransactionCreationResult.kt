package com.togethertrip.main.transaction.service.support

import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.user.domain.User

data class TransactionCreationResult(
    val trip: Trip,
    val actor: User,
    val actorParticipant: TripParticipant,
    val transaction: Transaction,
    val payments: List<TransactionPayment>,
    val shares: List<TransactionShare>,
)
