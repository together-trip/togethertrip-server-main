package com.togethertrip.main.triprecap.service.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalTripRecapImageStorageTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `recap 이미지를 로컬 스토리지에 저장하고 object key 와 URL 을 반환한다`() {
        val storage = LocalTripRecapImageStorage(
            storageDirectory = tempDir.toString(),
            publicUrlPrefix = "/uploads/trip-recaps",
        )

        val storedImage = storage.store(
            tripId = 10L,
            recapId = 100L,
            sceneOrder = 1,
            imageBytes = byteArrayOf(1, 2, 3),
        )

        assertEquals("trip-recaps/10/100/1.png", storedImage.objectKey)
        assertEquals("/uploads/trip-recaps/10/100/1.png", storedImage.imageUrl)
        assertContentEquals(
            byteArrayOf(1, 2, 3),
            Files.readAllBytes(tempDir.resolve("10/100/1.png")),
        )
    }

    @Test
    fun `object key 로 저장된 recap 이미지를 읽는다`() {
        val storage = LocalTripRecapImageStorage(
            storageDirectory = tempDir.toString(),
            publicUrlPrefix = "/uploads/trip-recaps",
        )
        storage.store(
            tripId = 10L,
            recapId = 100L,
            sceneOrder = 1,
            imageBytes = byteArrayOf(1, 2, 3),
        )

        val imageFile = storage.load("trip-recaps/10/100/1.png")

        assertContentEquals(byteArrayOf(1, 2, 3), imageFile.bytes)
        assertEquals("image/png", imageFile.contentType)
    }

    @Test
    fun `스토리지 밖으로 벗어나는 object key 는 거부한다`() {
        val storage = LocalTripRecapImageStorage(
            storageDirectory = tempDir.toString(),
            publicUrlPrefix = "/uploads/trip-recaps",
        )

        assertFailsWith<IllegalArgumentException> {
            storage.load("trip-recaps/../secret.png")
        }
    }
}
