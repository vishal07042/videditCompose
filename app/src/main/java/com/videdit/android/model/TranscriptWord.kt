package com.videdit.android.model

data class TranscriptWord(
    val word: String,
    val start: Double,
    val end: Double,
    val confidence: Double = 1.0,
)
