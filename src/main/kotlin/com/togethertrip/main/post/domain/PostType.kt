package com.togethertrip.main.post.domain

/**
 * 게시글 유형.
 * - NORMAL: 일반 여행 기록
 * - TRANSACTION_LOG: 거래 기반 기록 (transaction 연결)
 * - MEMORY: 추억/사진 기록
 */
enum class PostType {
    RECORD,
    EXPENSE
}
