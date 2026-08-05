package com.togethertrip.main.user.scheduler

import com.togethertrip.main.user.service.UserAccountDeletionCleanupDispatchService
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class UserAccountDeletionCleanupScheduler(
    private val dispatchService: UserAccountDeletionCleanupDispatchService,
    @Value("\${user.account-deletion-cleanup.enabled:true}")
    private val enabled: Boolean,
    @Value("\${user.account-deletion-cleanup.batch-size:50}")
    private val batchSize: Int,
) {

    @Scheduled(fixedDelayString = "\${user.account-deletion-cleanup.fixed-delay:PT5S}")
    fun dispatchDue() {
        if (!enabled) {
            return
        }
        dispatchService.dispatchDue(batchSize)
    }
}
