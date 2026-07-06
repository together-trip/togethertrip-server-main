package com.togethertrip.main.transaction.service.support

import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.transaction.domain.Transaction
import org.springframework.stereotype.Component

@Component
class LinkedExpensePostSynchronizer(
    private val postRepository: PostRepository,
) {

    fun syncMetadata(transaction: Transaction) {
        postRepository.findByTransactionIdAndDeletedAtIsNull(transaction.id)
            .forEach { post ->
                post.update(
                    title = post.title,
                    category = transaction.category,
                    content = post.content,
                    occurredAt = transaction.occurredAt,
                    placeName = post.placeName,
                    latitude = post.latitude,
                    longitude = post.longitude,
                )
            }
    }

    fun markDeleted(transaction: Transaction) {
        postRepository.findByTransactionIdAndDeletedAtIsNull(transaction.id)
            .forEach { it.markDeleted() }
    }
}
