package com.togethertrip.main.transaction.repository

import com.togethertrip.main.transaction.domain.Transaction
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionRepository : JpaRepository<Transaction, Long>
