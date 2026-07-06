package com.togethertrip.main.triprecap.service

import org.springframework.core.task.TaskExecutor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class TripRecapGenerationJobLauncher(
    @Qualifier("tripRecapGenerationTaskExecutor")
    private val tripRecapGenerationTaskExecutor: TaskExecutor,
    private val tripRecapGenerationService: TripRecapGenerationService,
) {

    fun launchAfterCommit(recapId: Long) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        launch(recapId)
                    }
                }
            )
            return
        }

        launch(recapId)
    }

    private fun launch(recapId: Long) {
        tripRecapGenerationTaskExecutor.execute {
            tripRecapGenerationService.generate(recapId)
        }
    }
}
