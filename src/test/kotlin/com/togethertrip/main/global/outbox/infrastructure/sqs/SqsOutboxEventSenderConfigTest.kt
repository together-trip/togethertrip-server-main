package com.togethertrip.main.global.outbox.infrastructure.sqs

import com.togethertrip.main.global.outbox.service.LoggingOutboxEventSender
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertIs

class SqsOutboxEventSenderConfigTest {

    private val config = SqsOutboxEventSenderConfig()

    @Test
    fun `queue url이 없으면 logging sender를 사용한다`() {
        val sender = config.outboxEventSender(
            properties = SqsOutboxProperties(queueUrl = ""),
            objectMapper = jacksonObjectMapper(),
        )

        assertIs<LoggingOutboxEventSender>(sender)
    }

    @Test
    fun `queue url이 있으면 sqs sender를 사용한다`() {
        val sender = config.outboxEventSender(
            properties = SqsOutboxProperties(
                queueUrl = "https://sqs.ap-northeast-2.amazonaws.com/123/togethertrip-test",
                region = "ap-northeast-2",
            ),
            objectMapper = jacksonObjectMapper(),
        )

        assertIs<SqsOutboxEventSender>(sender)
    }
}
