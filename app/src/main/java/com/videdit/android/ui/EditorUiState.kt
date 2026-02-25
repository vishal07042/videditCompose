package com.videdit.android.ui

import com.videdit.android.model.TimeSegment
import com.videdit.android.model.TranscriptWord
import com.videdit.android.model.VideoFile

data class EditorUiState(
    val video: VideoFile? = null,
    val transcript: List<TranscriptWord> = emptyList(),
    val deletedSegments: List<TimeSegment> = emptyList(),
    val isTranscribing: Boolean = false,
    val isExporting: Boolean = false,
    val language: String = "en",
    val statusMessage: String = "Pick a video to start.",
    val lastExportPath: String? = null,
)
