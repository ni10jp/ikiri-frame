package jp.ni10.ikiriframe.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhotoMetadataTest {
    @Test fun formatsPixelCameraMetadataToMinutesByDefault() {
        val metadata = PhotoMetadata("Pixel 10 Pro", 6.9, 1.7, 0.0025, 50, "2026:09:24 15:30:45")
        assertEquals("6.9 mm  ·  ƒ/1.7  ·  1/400 s  ·  ISO 50  ·  2026/09/24 15:30", metadata.detailLine)
    }

    @Test fun formatsLongExposuresAndFractions() {
        assertEquals("30 s", PhotoMetadata.shutter(30.0))
        assertEquals("1.3 s", PhotoMetadata.shutter(1.3))
        assertEquals("0.8 s", PhotoMetadata.shutter(0.8))
        assertEquals("1/125 s", PhotoMetadata.shutter(0.008))
        assertEquals("", PhotoMetadata.shutter(0.0))
        assertEquals("", PhotoMetadata.shutter(Double.NaN))
    }

    @Test fun missingOrBrokenExifDoesNotInventCameraOrDate() {
        assertEquals("", PhotoMetadata().model)
        assertEquals("", PhotoMetadata.formatDate("invalid"))
        assertFalse(PhotoMetadata().detailLine.contains("null"))
        assertFalse(PhotoMetadata().detailLine.contains("Pixel"))
    }
}
