package com.togethertrip.main.user.domain

enum class UserAccountDeletionCleanupStatus {
    PENDING,
    PROCESSING,
    FAILED,
    COMPLETED,
}
