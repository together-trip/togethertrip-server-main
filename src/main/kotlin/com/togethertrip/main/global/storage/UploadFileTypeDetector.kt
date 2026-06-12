package com.togethertrip.main.global.storage

import org.springframework.stereotype.Component

@Component
class UploadFileTypeDetector {

    fun detect(fileBytes: ByteArray): UploadFileType? {
        return when {
            hasSignature(fileBytes, JPEG_SIGNATURE) -> UploadFileType.JPEG
            hasSignature(fileBytes, PNG_SIGNATURE) -> UploadFileType.PNG
            hasMp4Signature(fileBytes) -> UploadFileType.MP4
            else -> null
        }
    }

    private fun hasSignature(
        fileBytes: ByteArray,
        signature: ByteArray,
    ): Boolean {
        return fileBytes.size >= signature.size &&
            signature.indices.all { fileBytes[it] == signature[it] }
    }

    private fun hasMp4Signature(fileBytes: ByteArray): Boolean {
        return fileBytes.size >= MP4_FILE_TYPE_BOX_END &&
            fileBytes[4] == 'f'.code.toByte() &&
            fileBytes[5] == 't'.code.toByte() &&
            fileBytes[6] == 'y'.code.toByte() &&
            fileBytes[7] == 'p'.code.toByte()
    }

    companion object {
        private const val MP4_FILE_TYPE_BOX_END = 12
        private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}
