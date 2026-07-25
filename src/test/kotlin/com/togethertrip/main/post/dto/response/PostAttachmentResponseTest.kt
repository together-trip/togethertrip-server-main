package com.togethertrip.main.post.dto.response

import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostAttachment
import com.togethertrip.main.post.domain.PostAttachmentType
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostAttachmentResponseTest {
    @Test
    fun `VIDEO 첨부는 별도 인증 썸네일 endpoint가 없으면 thumbnailUrl을 노출하지 않는다`() {
        val attachment = attachment(PostAttachmentType.VIDEO, "/uploads/video-thumbnail.jpg")

        val response = PostAttachmentResponse.from(attachment)

        assertEquals("/api/trips/10/posts/20/attachments/30", response.fileUrl)
        assertNull(response.thumbnailUrl)
    }

    @Test
    fun `IMAGE 첨부도 별도 인증 썸네일 endpoint가 없으면 thumbnailUrl을 노출하지 않는다`() {
        val attachment = attachment(PostAttachmentType.IMAGE, "/uploads/image-thumbnail.jpg")

        val response = PostAttachmentResponse.from(attachment)

        assertNull(response.thumbnailUrl)
    }

    private fun attachment(type: PostAttachmentType, thumbnailUrl: String?): PostAttachment {
        val user = User("작성자").apply { id = 1 }
        val trip = Trip(user, "여행", "KRW").apply { id = 10 }
        val participant = TripParticipant(
            trip = trip,
            user = user,
            displayName = user.nickname,
            participantRole = TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE,
        )
        val post = Post(trip = trip, author = participant, postType = PostType.RECORD).apply { id = 20 }
        return PostAttachment(
            post = post,
            attachmentType = type,
            fileUrl = "/uploads/original",
            thumbnailUrl = thumbnailUrl,
        ).apply { id = 30 }
    }
}
