package com.togethertrip.main.global.storage

enum class UploadMediaKind {
    IMAGE,
    VIDEO,
}

enum class UploadFileType(
    val mediaKind: UploadMediaKind,
    val extension: String,
    val mimeType: String,
) {
    JPEG(
        mediaKind = UploadMediaKind.IMAGE,
        extension = "jpg",
        mimeType = "image/jpeg",
    ),
    PNG(
        mediaKind = UploadMediaKind.IMAGE,
        extension = "png",
        mimeType = "image/png",
    ),
    MP4(
        mediaKind = UploadMediaKind.VIDEO,
        extension = "mp4",
        mimeType = "video/mp4",
    ),
}
