package com.togethertrip.main.post.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.post.controller.spec.ExpensePostApiSpec
import com.togethertrip.main.post.dto.request.CreateExpensePostRequest
import com.togethertrip.main.post.dto.response.CreateExpensePostResponse
import com.togethertrip.main.post.service.PostService
import com.togethertrip.main.trip.security.RequireActiveTripParticipant
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/trips/{tripId}/expense-posts")
class ExpensePostController(
    private val postService: PostService,
) : ExpensePostApiSpec {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @RequireActiveTripParticipant
    override fun createExpensePost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @Valid @ModelAttribute request: CreateExpensePostRequest,
    ): ApiResponse<CreateExpensePostResponse> {
        return ApiResponse.success(
            postService.createExpensePost(
                userId = authUser.userId,
                tripId = tripId,
                request = request,
            )
        )
    }
}
