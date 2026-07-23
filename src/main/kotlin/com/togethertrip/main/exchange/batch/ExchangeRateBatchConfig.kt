package com.togethertrip.main.exchange.batch

import com.togethertrip.main.exchange.service.ExchangeRateImportResult
import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import java.util.concurrent.ThreadPoolExecutor

@Configuration
class ExchangeRateBatchConfig : JdbcDefaultBatchConfiguration() {

    override fun getTaskExecutor(): TaskExecutor {
        return applicationContext.getBean("exchangeRateBatchTaskExecutor", TaskExecutor::class.java)
    }

    @Bean
    fun exchangeRateBatchTaskExecutor(): TaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 1
            queueCapacity = 0
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
        reader: ExchangeRateBackfillDateReader,
        processor: ExchangeRateBackfillItemProcessor,
        writer: ExchangeRateBackfillResultWriter,
    ): Step {
        return StepBuilder(ExchangeRateBackfillBatchConstants.STEP_NAME, jobRepository)
            .chunk<LocalDate, ExchangeRateImportResult>(CHUNK_SIZE)
            .transactionManager(transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .listener(reader)
            .listener(processor)
            .listener(writer)
            .build()
    }

    companion object {
        private const val CHUNK_SIZE = 10
    }
}
