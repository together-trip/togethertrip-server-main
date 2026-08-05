package com.togethertrip.main.user.service

data class UserAccountDeletionCleanupDispatchResult(
    val requestedCount: Int,
    val completedCount: Int,
    val failedCount: Int,
)
