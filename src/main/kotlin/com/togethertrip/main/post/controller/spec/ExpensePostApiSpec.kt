package com.togethertrip.main.post.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.post.dto.request.CreateExpensePostRequest
import com.togethertrip.main.post.dto.request.UpdateExpensePostRequest
import com.togethertrip.main.post.dto.response.CreateExpensePostResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "ExpensePost", description = "여행 소비 게시글 통합 API")
@SecurityRequirement(name = "bearerAuth")
interface ExpensePostApiSpec {

    @Operation(
        summary = "소비 게시글 통합 작성",
        description = "multipart/form-data로 거래와 소비 게시글을 하나의 트랜잭션에서 함께 생성합니다.",
    )
    fun createExpensePost(
        authUser: AuthUser,
        tripId: Long,
        request: CreateExpensePostRequest,
    ): ApiResponse<CreateExpensePostResponse>

    @Operation(
        summary = "소비 게시글 통합 수정",
        description = "multipart/form-data로 거래와 소비 게시글을 하나의 트랜잭션에서 함께 수정합니다.",
    )
    fun updateExpensePost(
        authUser: AuthUser,
        tripId: Long,
        postId: Long,
        request: UpdateExpensePostRequest,
    ): ApiResponse<CreateExpensePostResponse>
}
