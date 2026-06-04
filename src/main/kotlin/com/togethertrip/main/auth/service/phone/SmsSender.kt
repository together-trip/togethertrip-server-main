package com.togethertrip.main.auth.service.phone

interface SmsSender {
    fun validateSendable()

    fun sendVerificationCode(
        phoneNumber: String,
        code: String,
    )
}
