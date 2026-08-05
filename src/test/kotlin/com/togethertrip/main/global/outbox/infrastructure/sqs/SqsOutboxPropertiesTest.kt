package com.togethertrip.main.global.outbox.infrastructure.sqs

import kotlin.test.Test
import kotlin.test.assertFailsWith

class SqsOutboxPropertiesTest {

    @Test
    fun `FIFO queue type은 fifo suffix queue URL만 허용한다`() {
        assertFailsWith<IllegalArgumentException> {
            SqsOutboxProperties(
                queueUrl = "https://sqs.ap-northeast-2.amazonaws.com/123/togethertrip-test",
                queueType = SqsOutboxQueueType.FIFO,
            )
        }
    }

    @Test
    fun `STANDARD queue type은 fifo suffix queue URL을 허용하지 않는다`() {
        assertFailsWith<IllegalArgumentException> {
            SqsOutboxProperties(
                queueUrl = "https://sqs.ap-northeast-2.amazonaws.com/123/togethertrip-test.fifo",
                queueType = SqsOutboxQueueType.STANDARD,
            )
        }
    }
}
