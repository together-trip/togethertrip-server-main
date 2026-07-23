package com.togethertrip.main.global.outbox.service

data class OutboxEventDispatchResult(
    val requestedCount: Int,
    val publishedCount: Int,
    val failedCount: Int,
)
