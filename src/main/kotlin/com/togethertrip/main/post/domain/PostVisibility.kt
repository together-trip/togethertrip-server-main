package com.togethertrip.main.post.domain

/**
 * 게시글 공개 범위.
 * - TRIP_ONLY: 같은 여행방 참여자에게만 공개
 * - PUBLIC: 전체 공개
 */
enum class PostVisibility {
    TRIP_ONLY,
    PUBLIC
}
