package jp.ni10.ikiriframe.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameGeometryTest {
    @Test fun outputHasProportionalBarAtAnySourceResolution() {
        assertEquals(62, FrameGeometry.barHeight(720, 1f))
        assertEquals(124, FrameGeometry.barHeight(1440, 1f))
        assertEquals(248, FrameGeometry.barHeight(2880, 1f))
    }

    @Test fun thicknessIsMonotonicAndClamped() {
        val thin = FrameGeometry.barHeight(4000, FrameGeometry.MinimumThickness)
        val standard = FrameGeometry.barHeight(4000, 1f)
        val thick = FrameGeometry.barHeight(4000, FrameGeometry.MaximumThickness)
        assertTrue(thin < standard && standard < thick)
        assertEquals(thin, FrameGeometry.barHeight(4000, -1f))
        assertEquals(thick, FrameGeometry.barHeight(4000, 100f))
    }

    @Test fun longMetadataAndMaximumThicknessCannotPushContentOutsidePhoto() {
        for (width in listOf(1, 320, 1080, 4080)) {
            for (required in listOf(720f, 1100f, 2200f)) {
                for (thickness in listOf(0.65f, 1f, 1.75f)) {
                    val height = FrameGeometry.barHeight(width, thickness)
                    val scale = FrameGeometry.contentScale(width, height, required)
                    assertTrue(required * scale <= width + 0.001f)
                    assertTrue(FrameGeometry.BaseHeight * scale <= height + 0.001f)
                }
            }
        }
    }

    @Test fun disablingLogoRemovesItsEntireSeparatorAndSpacingBlock() {
        assertEquals(61f, FrameGeometry.logoBlockWidth(1f))
        assertEquals(0f, FrameGeometry.logoBlockWidth(null))
        assertEquals(173f, FrameGeometry.logoBlockWidth(5f))
    }
}
