package com.togethertrip.main.global.storage

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UploadFileTypeDetectorTest {

    private val detector = UploadFileTypeDetector()

    @Test
    fun `JPEG PNG MP4 파일 시그니처를 감지한다`() {
        assertEquals(UploadFileType.JPEG, detector.detect(jpegBytes()))
        assertEquals(UploadFileType.PNG, detector.detect(pngBytes()))
        assertEquals(UploadFileType.MP4, detector.detect(mp4Bytes()))
    }

    @Test
    fun `지원하지 않는 파일 시그니처는 null을 반환한다`() {
        assertNull(detector.detect("plain-text".toByteArray()))
        assertNull(detector.detect("<svg></svg>".toByteArray()))
    }

    private fun jpegBytes(): ByteArray {
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    }

    private fun pngBytes(): ByteArray {
        return byteArrayOf(
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

    private fun mp4Bytes(): ByteArray {
        return byteArrayOf(
            0x00,
            0x00,
            0x00,
            0x18,
            0x66,
            0x74,
            0x79,
            0x70,
            0x6D,
            0x70,
            0x34,
            0x32,
        )
    }
}
