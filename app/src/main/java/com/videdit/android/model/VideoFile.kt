package com.videdit.android.model

data class VideoFile(
    val uriString: String,
    val displayName: String,
    val localPath: String,
    val durationSec: Double,
)
