package com.togethertrip.main.post.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.post.controller.spec.PostApiSpec
import com.togethertrip.main.post.dto.request.CreatePostCommentRequest
import com.togethertrip.main.post.dto.request.CreatePostRequest
import com.togethertrip.main.post.dto.request.UpdatePostRequest
import com.togethertrip.main.post.service.PostService
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
) : PostApiSpec {

    @PostMapping
    override fun createPost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestBody request: CreatePostRequest,
    ) {
    }

    @GetMapping
    override fun getPosts(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @RequestParam(required = false) postType: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ) {
    }

    @GetMapping("/{postId}")
    override fun getPost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
    ) {
    }

    @PatchMapping("/{postId}")
    override fun updatePost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
        @RequestBody request: UpdatePostRequest,
    ) {
    }

    @DeleteMapping("/{postId}")
    override fun deletePost(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
    ) {
    }

    @PostMapping("/{postId}/comments")
    override fun createComment(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
        @RequestBody request: CreatePostCommentRequest,
    ) {
    }

    @GetMapping("/{postId}/comments")
    override fun getComments(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
    ) {
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    override fun deleteComment(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
    ) {
    }

}
