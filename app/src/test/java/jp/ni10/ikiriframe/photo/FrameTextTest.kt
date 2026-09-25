package jp.ni10.ikiriframe.photo

import org.junit.Assert.*
import org.junit.Test

class FrameTextTest {
    @Test fun defaultsUseExifFormattingAndBlankOverridesHideFields() {
        val metadata = PhotoMetadata("Pixel", 6.9, 1.7, 1.0 / 400, 50, "2026:09:24 15:30:45")
        val text = FrameText.from(metadata)
        assertEquals(metadata.detailLine, text.detailLine)
        assertEquals("50 mm  ·  ISO 200", text.copy(focalLength = "50 mm", aperture = "", shutter = "", iso = "ISO 200", takenAt = " ").detailLine)
    }
}
