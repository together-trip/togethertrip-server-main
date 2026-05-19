package com.togethertrip.main.global.response

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> {
            return ApiResponse(
                success = true,
                data = data,
                message = null,
            )
        }

        fun success(): ApiResponse<Unit> {
            return ApiResponse(
                success = true,
                data = Unit,
                message = null,
            )
        }

        fun <T> success(
            data: T,
            message: String,
        ): ApiResponse<T> {
            return ApiResponse(
                success = true,
                data = data,
                message = message,
            )
        }
    }
}