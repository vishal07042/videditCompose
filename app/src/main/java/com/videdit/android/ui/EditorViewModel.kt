package com.videdit.android.ui

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videdit.android.core.SegmentUtils
import com.videdit.android.data.VideoEditorRepository
import com.videdit.android.data.VideoFileLoader
import com.videdit.android.model.ExportSettings
import com.videdit.android.model.TimeSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoEditorRepository(application)
    private val fileLoader = VideoFileLoader(application)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.prepareBundledAssets()
            val readinessIssue = repository.transcriptionReadiness()
            _uiState.update {
                it.copy(
                    statusMessage = readinessIssue ?: "Ready. Pick a video and run transcription.",
                )
            }
        }
    }

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { fileLoader.load(uri) }
                .onSuccess { video ->
                    _uiState.update {
                        it.copy(
                            video = video,
                            transcript = emptyList(),
                            deletedSegments = emptyList(),
                            lastExportPath = null,
                            statusMessage = "Loaded ${video.displayName} (${"%.1f".format(video.durationSec)}s)",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(statusMessage = "Video load failed: ${error.message}") }
                }
        }
    }

    fun setLanguage(language: String) {
        _uiState.update { it.copy(language = language.trim().ifBlank { "en" }) }
    }

    fun transcribe() {
        val state = _uiState.value
        val video = state.video ?: return
        val readinessIssue = repository.transcriptionReadiness()
        if (readinessIssue != null) {
            _uiState.update { it.copy(statusMessage = readinessIssue) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTranscribing = true, statusMessage = "Transcribing with whisper.cpp...") }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.transcribe(video, _uiState.value.language) }
            }

            result.onSuccess { words ->
                _uiState.update {
                    it.copy(
                        transcript = words,
                        deletedSegments = emptyList(),
                        isTranscribing = false,
                        statusMessage = "Transcription complete: ${words.size} words",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isTranscribing = false,
                        statusMessage = "Transcription failed: ${error.message}",
                    )
                }
            }
        }
    }

    fun toggleWordDeletion(index: Int) {
        val words = _uiState.value.transcript
        if (index !in words.indices) return
        val word = words[index]
        addDeletedSegment(TimeSegment(word.start, word.end))
    }

    fun deleteTextRange(startIndex: Int, endIndex: Int) {
        val words = _uiState.value.transcript
        if (words.isEmpty()) return

        val safeStart = startIndex.coerceIn(words.indices)
        val safeEnd = endIndex.coerceIn(words.indices)
        if (safeEnd < safeStart) return

        addDeletedSegment(TimeSegment(words[safeStart].start, words[safeEnd].end))
    }

    fun undoLastDelete() {
        _uiState.update { current ->
            if (current.deletedSegments.isEmpty()) current
            else current.copy(
                deletedSegments = current.deletedSegments.dropLast(1),
                statusMessage = "Removed last deletion",
            )
        }
    }

    fun clearDeletedSegments() {
        _uiState.update {
            it.copy(
                deletedSegments = emptyList(),
                statusMessage = "Cleared all deletion segments",
            )
        }
    }

    fun exportVideo(settings: ExportSettings = ExportSettings()) {
        val state = _uiState.value
        val video = state.video ?: return
        val readinessIssue = repository.exportReadiness()
        if (readinessIssue != null) {
            _uiState.update { it.copy(statusMessage = readinessIssue) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, statusMessage = "Exporting edited video...") }
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.export(video, _uiState.value.deletedSegments, settings) }
            }

            result.onSuccess { file ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        lastExportPath = file.absolutePath,
                        statusMessage = "Export complete: ${file.absolutePath}",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isExporting = false, statusMessage = "Export failed: ${error.message}")
                }
            }
        }
    }

    fun needsManageAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
    }

    fun needsLegacyWritePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return false
        return getApplication<Application>().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionStatusUpdated() {
        val readinessIssue = repository.exportReadiness()
        _uiState.update {
            it.copy(
                statusMessage = readinessIssue ?: "Storage access ready. Export target: /storage/emulated/0/videidit",
            )
        }
    }

    private fun addDeletedSegment(segment: TimeSegment) {
        _uiState.update { current ->
            val merged = SegmentUtils.mergeSegments(current.deletedSegments + segment)
            val keptDuration = SegmentUtils.totalDuration(
                SegmentUtils.keptSegments(current.video?.durationSec ?: 0.0, merged),
            )
            current.copy(
                deletedSegments = merged,
                statusMessage = "Cut marks: ${merged.size} | Remaining: ${"%.1f".format(keptDuration)}s",
            )
        }
    }
}
