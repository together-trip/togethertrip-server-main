package com.togethertrip.main.global.logging

import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RequestIdGenerator {

    fun generate(): String {
        return UUID.randomUUID().toString()
    }
}
