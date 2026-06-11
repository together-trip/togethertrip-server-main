package com.togethertrip.main.exchange.batch

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.JobRegistry
import org.springframework.batch.core.configuration.support.MapJobRegistry
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.transaction.PlatformTransactionManager

@Configuration
@EnableBatchProcessing
class ExchangeRateBatchConfig {

    @Bean
    fun jobRegistry(): JobRegistry {
        return MapJobRegistry()
    }

    @Bean
    fun asyncJobOperator(
        jobRepository: JobRepository,
        jobRegistry: JobRegistry,
    ): JobOperator {
        val jobOperator = TaskExecutorJobOperator()
        jobOperator.setJobRepository(jobRepository)
        jobOperator.setJobRegistry(jobRegistry)
        jobOperator.setTaskExecutor(SimpleAsyncTaskExecutor("exchange-rate-batch-"))
        jobOperator.afterPropertiesSet()
        return jobOperator
    }

    @Bean
    fun exchangeRateBackfillJob(
        jobRepository: JobRepository,
        exchangeRateBackfillStep: Step,
        listener: ExchangeRateBackfillJobListener,
    ): Job {
        return JobBuilder(ExchangeRateBackfillBatchConstants.JOB_NAME, jobRepository)
            .listener(listener)
            .start(exchangeRateBackfillStep)
            .build()
    }

    @Bean
    fun exchangeRateBackfillStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        tasklet: ExchangeRateBackfillTasklet,
    ): Step {
        return StepBuilder(ExchangeRateBackfillBatchConstants.STEP_NAME, jobRepository)
            .tasklet(tasklet, transactionManager)
            .build()
    }
}
