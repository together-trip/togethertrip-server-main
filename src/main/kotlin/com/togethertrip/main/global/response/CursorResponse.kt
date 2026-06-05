package com.togethertrip.main.global.response

data class CursorResponse<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasNext: Boolean,
    val size: Int,
)
