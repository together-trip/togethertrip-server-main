package com.togethertrip.main.transaction.repository

import com.togethertrip.main.transaction.domain.TransactionShare
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionShareRepository : JpaRepository<TransactionShare, Long>
