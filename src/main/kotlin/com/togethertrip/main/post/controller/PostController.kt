package com.togethertrip.main.post.controller

import com.togethertrip.main.post.dto.request.CreatePostCommentRequest
import com.togethertrip.main.post.dto.request.CreatePostRequest
import com.togethertrip.main.post.dto.request.UpdatePostRequest
import com.togethertrip.main.post.service.PostService
import com.togethertrip.main.global.security.principal.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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

@Tag(name = "Post", description = "여행 커뮤니티 게시글 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/trips/{tripId}/posts")
class PostController(
    private val tripPostService: PostService,
) {

    @Operation(summary = "게시글 작성", description = "거래 기반 기록 또는 일반 여행 기록을 작성합니다. 첨부 파일을 함께 등록할 수 있습니다.")
    @PostMapping
    fun createPost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: CreatePostRequest,
    ) {
    }

    @Operation(summary = "게시글 목록 조회", description = "여행방의 게시글 목록을 최신순으로 조회합니다. 게시글 유형으로 필터링할 수 있습니다.")
    @GetMapping
    fun getPosts(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) postType: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ) {
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글 본문, 첨부 파일, 댓글, 좋아요 수를 조회합니다.")
    @GetMapping("/{postId}")
    fun getPost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
    ) {
    }

    @Operation(summary = "게시글 수정", description = "게시글 본문과 공개 범위를 수정합니다.")
    @PatchMapping("/{postId}")
    fun updatePost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
        @RequestBody request: UpdatePostRequest,
    ) {
    }

    @Operation(summary = "게시글 삭제", description = "게시글을 소프트 삭제합니다.")
    @DeleteMapping("/{postId}")
    fun deletePost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
    ) {
    }

    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 작성합니다.")
    @PostMapping("/{postId}/comments")
    fun createComment(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
        @RequestBody request: CreatePostCommentRequest,
    ) {
    }

    @Operation(summary = "댓글 목록 조회", description = "게시글의 댓글 목록을 작성순으로 조회합니다.")
    @GetMapping("/{postId}/comments")
    fun getComments(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
    ) {
    }

    @Operation(summary = "댓글 삭제", description = "댓글을 소프트 삭제합니다.")
    @DeleteMapping("/{postId}/comments/{commentId}")
    fun deleteComment(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
    ) {
    }

    @Operation(summary = "좋아요 등록", description = "게시글에 좋아요를 등록합니다. 참여자당 한 번만 가능합니다.")
    @PostMapping("/{postId}/likes")
    fun likePost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
    ) {
    }

    @Operation(summary = "좋아요 취소", description = "게시글 좋아요를 취소합니다.")
    @DeleteMapping("/{postId}/likes")
    fun unlikePost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
    ) {
    }
}
