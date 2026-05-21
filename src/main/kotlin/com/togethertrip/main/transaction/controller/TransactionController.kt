package com.togethertrip.main.transaction.controller

import com.togethertrip.main.transaction.service.TransactionService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Transaction", description = "거래 API")
@RestController
@RequestMapping("/api/trips/{tripId}/transactions")
class TransactionController(
    private val transactionService: TransactionService,
)
