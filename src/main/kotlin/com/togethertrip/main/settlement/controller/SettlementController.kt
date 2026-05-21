package com.togethertrip.main.settlement.controller

import com.togethertrip.main.settlement.service.SettlementService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Settlement", description = "정산 API")
@RestController
@RequestMapping("/api/trips/{tripId}/settlements")
class SettlementController(
    private val settlementService: SettlementService,
)
