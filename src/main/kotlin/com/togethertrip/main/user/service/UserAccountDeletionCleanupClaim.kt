package com.togethertrip.main.user.service

import com.togethertrip.main.user.domain.UserAccountDeletionCleanupType

data class UserAccountDeletionCleanupClaim(
    val taskId: Long,
    val claimId: String,
    val userId: Long,
    val type: UserAccountDeletionCleanupType,
    val payload: String?,
)
