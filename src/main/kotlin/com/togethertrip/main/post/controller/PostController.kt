package com.togethertrip.main.post.controller

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.response.CursorResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.post.controller.spec.PostApiSpec
import com.togethertrip.main.post.dto.request.CreatePostCommentRequest
import com.togethertrip.main.post.dto.request.CreatePostRequest
import com.togethertrip.main.post.dto.request.UpdatePostRequest
import com.togethertrip.main.post.dto.response.PostCommentResponse
import com.togethertrip.main.post.dto.response.PostDetailResponse
import com.togethertrip.main.post.dto.response.PostSummaryResponse
import com.togethertrip.main.post.service.PostDeleteService
import com.togethertrip.main.post.service.PostService
import com.togethertrip.main.trip.security.RequireActiveTripParticipant
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/trips/{tripId}/posts")
class PostController(
    private val tripPostService: PostService,
    private val postDeleteService: PostDeleteService,
) : PostApiSpec {

    @PostMapping
    @RequireActiveTripParticipant
    override fun createPost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @Valid @RequestBody request: CreatePostRequest,
    ): ApiResponse<PostDetailResponse> {
        return ApiResponse.success(
            tripPostService.createPost(
                userId = authUser.userId,
                tripId = tripId,
                request = request,
            )
        )
    }

    @GetMapping
    @RequireActiveTripParticipant
    override fun getPosts(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) postType: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<CursorResponse<PostSummaryResponse>> {
        return ApiResponse.success(
            tripPostService.getPosts(
                tripId = tripId,
                postType = postType,
                cursor = cursor,
                size = size,
            )
        )
    }

    @GetMapping("/{postId}")
    @RequireActiveTripParticipant
    override fun getPost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
    ): ApiResponse<PostDetailResponse> {
        return ApiResponse.success(
            tripPostService.getPost(
                tripId = tripId,
                postId = postId,
            )
        )
    }

    @PatchMapping("/{postId}")
    @RequireActiveTripParticipant
    override fun updatePost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
        @Valid @RequestBody request: UpdatePostRequest,
    ): ApiResponse<PostDetailResponse> {
        return ApiResponse.success(
            tripPostService.updatePost(
                userId = authUser.userId,
                tripId = tripId,
                postId = postId,
                request = request,
            )
        )
    }

    @DeleteMapping("/{postId}")
    @RequireActiveTripParticipant
    override fun deletePost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
    ): ApiResponse<Unit> {
        postDeleteService.deletePost(
            userId = authUser.userId,
            tripId = tripId,
            postId = postId,
        )

        return ApiResponse.success()
    }

    @PostMapping("/{postId}/comments")
    @RequireActiveTripParticipant
    override fun createComment(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
        @Valid @RequestBody request: CreatePostCommentRequest,
    ): ApiResponse<PostCommentResponse> {
        return ApiResponse.success(
            tripPostService.createComment(
                userId = authUser.userId,
                tripId = tripId,
                postId = postId,
                request = request,
            )
        )
    }

    @GetMapping("/{postId}/comments")
    @RequireActiveTripParticipant
    override fun getComments(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<CursorResponse<PostCommentResponse>> {
        return ApiResponse.success(
            tripPostService.getComments(
                tripId = tripId,
                postId = postId,
                cursor = cursor,
                size = size,
            )
        )
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    @RequireActiveTripParticipant
    override fun deleteComment(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
    ): ApiResponse<Unit> {
        tripPostService.deleteComment(
            userId = authUser.userId,
            tripId = tripId,
            postId = postId,
            commentId = commentId,
        )

        return ApiResponse.success()
    }

}
