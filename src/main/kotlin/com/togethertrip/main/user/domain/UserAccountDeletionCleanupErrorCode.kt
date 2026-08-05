package com.togethertrip.main.user.domain

enum class UserAccountDeletionCleanupErrorCode {
    APPLE_TOKEN_REVOCATION_FAILED,
    PROFILE_IMAGE_DELETE_FAILED,
    REDIS_REFRESH_TOKEN_DELETE_FAILED,
    INVALID_TASK_PAYLOAD,
}
