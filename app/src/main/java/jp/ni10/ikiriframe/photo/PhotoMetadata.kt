package jp.ni10.ikiriframe.photo

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class PhotoMetadata(
    val model: String = "",
    val focalLength: Double? = null,
    val aperture: Double? = null,
    val exposureSeconds: Double? = null,
    val iso: Int? = null,
    val takenAt: String? = null,
    val originalExif: Map<String, String> = emptyMap(),
) {
    val detailLine: String
        get() = FrameText.from(this).detailLine

    companion object {
        fun decimal(value: Double): String =
            if (abs(value - value.toInt()) < 0.0001) value.toInt().toString()
            else String.format(Locale.US, "%.1f", value)

        fun shutter(seconds: Double): String {
            if (!seconds.isFinite() || seconds <= 0) return ""
            return if (seconds <= 0.5) "1/${(1 / seconds).roundToInt()} s"
            else "${decimal(seconds)} s"
        }

        fun formatDate(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return runCatching {
                LocalDateTime.parse(raw.trim(), DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
            }.getOrDefault("")
        }
    }
}
