package com.togethertrip.main.triprecap.service.storage

import com.togethertrip.main.global.storage.UploadFileTypeDetector
import com.togethertrip.main.triprecap.service.ai.TripRecapPhotoContent
import com.togethertrip.main.triprecap.service.ai.TripRecapPhotoContentLoader
import com.togethertrip.main.triprecap.service.ai.TripRecapPhotoReference
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@Component
class LocalTripRecapPhotoContentLoader(
    @Value("\${post.attachments.local-storage-path}")
    private val storagePath: String,
    @Value("\${post.attachments.public-url-prefix}")
    private val publicUrlPrefix: String,
    @Value("\${trip-recap.ai.openai.max-reference-image-bytes:10485760}")
    private val maxReferenceImageBytes: Long,
    private val uploadFileTypeDetector: UploadFileTypeDetector,
) : TripRecapPhotoContentLoader {

    override fun load(reference: TripRecapPhotoReference): TripRecapPhotoContent? {
        val urlPath = runCatching { URI.create(reference.imageUrl).path }.getOrNull() ?: return null
        val normalizedPrefix = "/${publicUrlPrefix.trim('/')}"
        if (!urlPath.startsWith("$normalizedPrefix/")) {
            return null
        }

        val relativePath = urlPath.removePrefix(normalizedPrefix).trimStart('/')
        if (relativePath.isBlank()) {
            return null
        }

        val storageDirectory = Path.of(storagePath).toAbsolutePath().normalize()
        val targetPath = storageDirectory.resolve(relativePath).normalize()
        if (!targetPath.startsWith(storageDirectory) || !Files.isRegularFile(targetPath)) {
            return null
        }
        if (Files.size(targetPath) > maxReferenceImageBytes) {
            return null
        }

        val bytes = Files.readAllBytes(targetPath)
        val fileType = uploadFileTypeDetector.detect(bytes) ?: return null
        if (!fileType.mimeType.startsWith("image/")) {
            return null
        }

        return TripRecapPhotoContent(
            filename = targetPath.fileName.toString(),
            contentType = fileType.mimeType,
            bytes = bytes,
        )
    }
}
