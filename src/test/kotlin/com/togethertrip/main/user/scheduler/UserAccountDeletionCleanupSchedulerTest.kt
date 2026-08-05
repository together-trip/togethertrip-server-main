package com.togethertrip.main.user.scheduler

import com.togethertrip.main.user.service.UserAccountDeletionCleanupDispatchService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class UserAccountDeletionCleanupSchedulerTest {

    @Test
    fun `비활성 설정이면 정리 작업을 실행하지 않는다`() {
        val service = mock(UserAccountDeletionCleanupDispatchService::class.java)
        val scheduler = UserAccountDeletionCleanupScheduler(service, enabled = false, batchSize = 30)

        scheduler.dispatchDue()

        verify(service, never()).dispatchDue(30)
    }

    @Test
    fun `활성 설정이면 지정한 batch 크기로 정리 작업을 실행한다`() {
        val service = mock(UserAccountDeletionCleanupDispatchService::class.java)
        val scheduler = UserAccountDeletionCleanupScheduler(service, enabled = true, batchSize = 30)

        scheduler.dispatchDue()

        verify(service).dispatchDue(30)
    }
}
