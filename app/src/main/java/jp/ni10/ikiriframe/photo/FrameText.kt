package jp.ni10.ikiriframe.photo

/** Display overrides only. The source photo and its embedded EXIF stay intact. */
data class FrameText(
    val model: String,
    val focalLength: String,
    val aperture: String,
    val shutter: String,
    val iso: String,
    val takenAt: String,
) {
    val detailLine: String get() = values().drop(1).filter { it.isNotBlank() }.joinToString("  ·  ")
    fun values() = listOf(model, focalLength, aperture, shutter, iso, takenAt)

    companion object {
        fun from(metadata: PhotoMetadata) = FrameText(
            metadata.model,
            metadata.focalLength?.let { "${PhotoMetadata.decimal(it)} mm" }.orEmpty(),
            metadata.aperture?.let { "ƒ/${PhotoMetadata.decimal(it)}" }.orEmpty(),
            metadata.exposureSeconds?.let(PhotoMetadata::shutter).orEmpty(),
            metadata.iso?.let { "ISO $it" }.orEmpty(),
            PhotoMetadata.formatDate(metadata.takenAt),
        )
        fun from(values: List<String>) = FrameText(values[0], values[1], values[2], values[3], values[4], values[5])
    }
}
