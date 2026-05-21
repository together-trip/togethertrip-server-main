package com.togethertrip.main.transaction.repository

import com.togethertrip.main.transaction.domain.TransactionPayment
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionPaymentRepository : JpaRepository<TransactionPayment, Long>
