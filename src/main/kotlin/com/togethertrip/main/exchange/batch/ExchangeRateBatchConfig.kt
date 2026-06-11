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
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.interceptor.DefaultTransactionAttribute
import java.util.concurrent.ThreadPoolExecutor

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
        exchangeRateBatchTaskExecutor: TaskExecutor,
    ): JobOperator {
        val jobOperator = TaskExecutorJobOperator()
        jobOperator.setJobRepository(jobRepository)
        jobOperator.setJobRegistry(jobRegistry)
        jobOperator.setTaskExecutor(exchangeRateBatchTaskExecutor)
        jobOperator.afterPropertiesSet()
        return jobOperator
    }

    @Bean
    fun exchangeRateBatchTaskExecutor(): TaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            setCorePoolSize(1)
            setMaxPoolSize(1)
            setQueueCapacity(0)
            setThreadNamePrefix("exchange-rate-batch-")
            setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
            initialize()
        }
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
            .transactionAttribute(DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_NOT_SUPPORTED))
            .build()
    }
}
