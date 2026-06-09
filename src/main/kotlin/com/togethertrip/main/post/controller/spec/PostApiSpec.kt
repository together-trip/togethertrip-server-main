package com.togethertrip.main.post.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.response.CursorResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.post.dto.request.CreatePostCommentRequest
import com.togethertrip.main.post.dto.request.CreatePostRequest
import com.togethertrip.main.post.dto.request.UpdatePostRequest
import com.togethertrip.main.post.dto.response.PostCommentResponse
import com.togethertrip.main.post.dto.response.PostDetailResponse
import com.togethertrip.main.post.dto.response.PostSummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Post", description = "여행 커뮤니티 게시글 API")
@SecurityRequirement(name = "bearerAuth")
interface PostApiSpec {

    @Operation(summary = "게시글 작성", description = "거래 기반 기록 또는 일반 여행 기록을 작성합니다. 첨부 파일을 함께 등록할 수 있습니다.")
    fun createPost(
        authUser: AuthUser,
        tripId: Long,
        request: CreatePostRequest,
    ): ApiResponse<PostDetailResponse>

    @Operation(summary = "게시글 목록 조회", description = "여행방의 게시글 목록을 최신순으로 조회합니다. 게시글 유형으로 필터링할 수 있으며 첨부 미리보기를 포함합니다.")
    fun getPosts(
        authUser: AuthUser,
        tripId: Long,
        postType: String?,
        cursor: String?,
        size: Int?,
    ): ApiResponse<CursorResponse<PostSummaryResponse>>

    @Operation(summary = "게시글 상세 조회", description = "게시글 본문, 첨부 파일, 댓글을 조회합니다.")
    fun getPost(
        authUser: AuthUser,
        tripId: Long,
        postId: Long,
    ): ApiResponse<PostDetailResponse>

    @Operation(summary = "게시글 수정", description = "게시글 제목, 카테고리, 본문, 날짜, 위치, 첨부 목록을 수정합니다. attachments가 null이면 기존 첨부를 유지하고, 빈 배열이면 전체 제거합니다.")
    fun updatePost(
        authUser: AuthUser,
        tripId: Long,
        postId: Long,
        request: UpdatePostRequest,
    ): ApiResponse<PostDetailResponse>

    @Operation(summary = "게시글 삭제", description = "게시글을 소프트 삭제합니다. 소비 게시글은 연결 거래를 같은 트랜잭션에서 무효 처리합니다.")
    fun deletePost(
        authUser: AuthUser,
        tripId: Long,
        postId: Long,
    ): ApiResponse<Unit>

    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 작성합니다.")
    fun createComment(
        authUser: AuthUser,
        tripId: Long,
        postId: Long,
        request: CreatePostCommentRequest,
    ): ApiResponse<PostCommentResponse>

    @Operation(summary = "댓글 목록 조회", description = "게시글의 원댓글 목록을 작성순 cursor 방식으로 조회합니다.")
    fun getComments(
        authUser: AuthUser,
        tripId: Long,
        postId: Long,
        cursor: String?,
        size: Int?,
    ): ApiResponse<CursorResponse<PostCommentResponse>>

    @Operation(summary = "댓글 삭제", description = "원댓글을 소프트 삭제합니다.")
    fun deleteComment(
        authUser: AuthUser,
        tripId: Long,
        postId: Long,
        commentId: Long,
    ): ApiResponse<Unit>
}
