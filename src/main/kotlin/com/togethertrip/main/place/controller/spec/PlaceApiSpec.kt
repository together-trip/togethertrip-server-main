package com.togethertrip.main.place.controller.spec

import com.togethertrip.main.global.response.ApiResponse
import com.togethertrip.main.global.security.principal.AuthUser
import com.togethertrip.main.place.dto.PlaceDetailResponse
import com.togethertrip.main.place.dto.PlaceSuggestionResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

@Tag(name = "Place", description = "여행 기록 장소 검색 API")
@SecurityRequirement(name = "bearerAuth")
interface PlaceApiSpec {
    @Operation(
        summary = "장소 자동완성",
        description = "인증된 여행 참여자가 장소명 또는 주소로 글로벌 장소 후보를 조회합니다.",
    )
    fun autocomplete(
        authUser: AuthUser,
        tripId: Long,
        @Size(min = 2, max = 100) query: String,
        @Size(max = 100) sessionToken: String?,
        @Pattern(regexp = "[A-Za-z-]{2,10}") languageCode: String,
    ): ApiResponse<List<PlaceSuggestionResponse>>

    @Operation(
        summary = "장소 상세 조회",
        description = "자동완성에서 선택한 장소의 표시명, 주소, 위도와 경도를 조회합니다.",
    )
    fun getPlace(
        authUser: AuthUser,
        tripId: Long,
        @Size(min = 1, max = 255) placeId: String,
        @Size(max = 100) sessionToken: String?,
        @Pattern(regexp = "[A-Za-z-]{2,10}") languageCode: String,
    ): ApiResponse<PlaceDetailResponse>

    @Operation(
        summary = "좌표 역지오코딩",
        description = "지도에서 선택한 위도와 경도를 표시 가능한 장소명과 주소로 변환합니다.",
    )
    fun reverseGeocode(
        authUser: AuthUser,
        tripId: Long,
        @DecimalMin("-90") @DecimalMax("90") latitude: BigDecimal,
        @DecimalMin("-180") @DecimalMax("180") longitude: BigDecimal,
        @Pattern(regexp = "[A-Za-z-]{2,10}") languageCode: String,
    ): ApiResponse<PlaceDetailResponse>
}
