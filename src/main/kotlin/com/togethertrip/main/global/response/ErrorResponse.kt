package com.togethertrip.main.global.response

data class ErrorResponse(
    val success: Boolean = false,
    val code: String,
    val message: String,
)