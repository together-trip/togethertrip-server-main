package com.togethertrip.main.global.outbox.infrastructure.sqs

enum class SqsOutboxQueueType {
    STANDARD,
    FIFO,
    ;

    fun matches(queueUrl: String): Boolean {
        val fifoUrl = queueUrl.substringBefore('?').endsWith(FIFO_SUFFIX)
        return when (this) {
            STANDARD -> !fifoUrl
            FIFO -> fifoUrl
        }
    }

    private companion object {
        const val FIFO_SUFFIX = ".fifo"
    }
}
