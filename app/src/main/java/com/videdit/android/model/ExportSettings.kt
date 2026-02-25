package com.videdit.android.model

enum class ExportQuality { HIGH, MEDIUM, LOW }
enum class ExportResolution { ORIGINAL, P1080, P720, P480, P360 }

data class ExportSettings(
    val quality: ExportQuality = ExportQuality.MEDIUM,
    val resolution: ExportResolution = ExportResolution.ORIGINAL,
) {
    val crf: String
        get() = when (quality) {
            ExportQuality.HIGH -> "18"
            ExportQuality.MEDIUM -> "23"
            ExportQuality.LOW -> "28"
        }

    val preset: String
        get() = when (quality) {
            ExportQuality.HIGH -> "slow"
            ExportQuality.MEDIUM -> "medium"
            ExportQuality.LOW -> "veryfast"
        }

    val audioBitrate: String
        get() = when (quality) {
            ExportQuality.HIGH -> "192k"
            ExportQuality.MEDIUM -> "128k"
            ExportQuality.LOW -> "96k"
        }

    val scaleFilter: String?
        get() = when (resolution) {
            ExportResolution.P1080 -> "scale=1920:1080:force_original_aspect_ratio=decrease"
            ExportResolution.P720 -> "scale=1280:720:force_original_aspect_ratio=decrease"
            ExportResolution.P480 -> "scale=854:480:force_original_aspect_ratio=decrease"
            ExportResolution.P360 -> "scale=640:360:force_original_aspect_ratio=decrease"
            ExportResolution.ORIGINAL -> null
        }
}
