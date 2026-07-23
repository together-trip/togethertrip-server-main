package com.togethertrip.main.post.dto.response

import com.togethertrip.main.transaction.dto.response.TransactionDetailResponse

data class CreateExpensePostResponse(
    val post: PostDetailResponse,
    val transaction: TransactionDetailResponse,
)
