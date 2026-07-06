package com.togethertrip.main.triprecap.service.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.NoSuchFileException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Component
class LocalTripRecapImageStorage(
    @Value("\${trip-recap.images.storage-dir:build/trip-recap-images}")
    private val storageDirectory: String,
    @Value("\${trip-recap.images.public-url-prefix:/uploads/trip-recaps}")
    private val publicUrlPrefix: String,
) : TripRecapImageStorage {

    override fun store(
        tripId: Long,
        recapId: Long,
        sceneOrder: Int,
        imageBytes: ByteArray,
    ): TripRecapStoredImage {
        require(imageBytes.isNotEmpty()) { "imageBytes must not be empty" }

        val objectKey = "trip-recaps/$tripId/$recapId/$sceneOrder.png"
        val targetPath = Path.of(storageDirectory).resolve("$tripId/$recapId/$sceneOrder.png")
        Files.createDirectories(targetPath.parent)
        Files.write(
            targetPath,
            imageBytes,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )

        return TripRecapStoredImage(
            objectKey = objectKey,
            imageUrl = "${publicUrlPrefix.trimEnd('/')}/$tripId/$recapId/$sceneOrder.png",
        )
    }

    override fun load(objectKey: String): TripRecapStoredImageFile {
        val relativePath = toRelativePath(objectKey)
        val storagePath = Path.of(storageDirectory).normalize()
        val targetPath = storagePath.resolve(relativePath).normalize()
        require(targetPath.startsWith(storagePath)) { "invalid trip recap image object key" }
        if (!Files.isRegularFile(targetPath)) {
            throw NoSuchFileException(objectKey)
        }

        return TripRecapStoredImageFile(
            bytes = Files.readAllBytes(targetPath),
            contentType = Files.probeContentType(targetPath) ?: "image/png",
        )
    }

    private fun toRelativePath(objectKey: String): Path {
        val prefix = "trip-recaps/"
        require(objectKey.startsWith(prefix)) { "invalid trip recap image object key" }
        val relativeObjectKey = objectKey.removePrefix(prefix)
        require(relativeObjectKey.isNotBlank()) { "invalid trip recap image object key" }
        return Path.of(relativeObjectKey)
    }
}
