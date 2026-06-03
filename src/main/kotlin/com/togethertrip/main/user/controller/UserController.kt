package com.togethertrip.main.user.controller

import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.user.controller.spec.UserApiSpec
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import com.togethertrip.main.user.service.UserService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) : UserApiSpec {

    @GetMapping("/me")
    override fun getMe(
        @AuthenticationPrincipal authUser: AuthUser,
    ) {
    }

    @PatchMapping("/me")
    override fun updateMe(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestBody request: UpdateUserRequest,
    ) {
    }

    @DeleteMapping("/me")
    override fun deleteMe(
        @AuthenticationPrincipal authUser: AuthUser,
    ) {
    }

    @GetMapping("/me/trip-participants")
    override fun getMyTripParticipant(
        @AuthenticationPrincipal authUser: AuthUser,
        @RequestParam tripId: Long,
    ) {
    }
}
