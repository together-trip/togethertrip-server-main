package com.togethertrip.main.triprecap.controller

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.triprecap.controller.spec.TripRecapApiSpec
import com.togethertrip.main.triprecap.dto.request.TripRecapCreateRequest
import com.togethertrip.main.triprecap.dto.request.TripRecapRetryRequest
import com.togethertrip.main.triprecap.dto.response.TripRecapCreateResponse
import com.togethertrip.main.triprecap.dto.response.TripRecapResponse
import com.togethertrip.main.triprecap.dto.response.TripRecapStatusResponse
import com.togethertrip.main.triprecap.service.TripRecapService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/trips/{tripId}/recap")
class TripRecapController(
    private val tripRecapService: TripRecapService,
) : TripRecapApiSpec {

    @GetMapping("/status")
    override fun getStatus(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<TripRecapStatusResponse> {
        return ApiResponse.success(
            tripRecapService.getStatus(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }

    @PostMapping
    override fun create(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @Valid @RequestBody request: TripRecapCreateRequest,
    ): ApiResponse<TripRecapCreateResponse> {
        return ApiResponse.success(
            tripRecapService.create(
                userId = authUser.userId,
                tripId = tripId,
                style = request.style ?: throw BusinessException(CommonErrorCode.INVALID_INPUT),
            )
        )
    }

    @PostMapping("/retry")
    override fun retry(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @Valid @RequestBody request: TripRecapRetryRequest,
    ): ApiResponse<TripRecapCreateResponse> {
        return ApiResponse.success(
            tripRecapService.retry(
                userId = authUser.userId,
                tripId = tripId,
                style = request.style ?: throw BusinessException(CommonErrorCode.INVALID_INPUT),
            )
        )
    }

    @GetMapping
    override fun getRecap(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
    ): ApiResponse<TripRecapResponse> {
        return ApiResponse.success(
            tripRecapService.getRecap(
                userId = authUser.userId,
                tripId = tripId,
            )
        )
    }

    @GetMapping("/scenes/{sceneId}/image")
    override fun getSceneImage(
        @AuthenticationPrincipal authUser: AuthUser,
        @PathVariable tripId: Long,
        @PathVariable sceneId: Long,
    ): ResponseEntity<ByteArray> {
        val imageFile = tripRecapService.getSceneImage(
            userId = authUser.userId,
            tripId = tripId,
            sceneId = sceneId,
        )

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(imageFile.contentType))
            .body(imageFile.bytes)
    }
}
