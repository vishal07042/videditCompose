package com.videdit.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.videdit.android.model.TimeSegment
import com.videdit.android.model.TranscriptWord
import com.videdit.android.ui.theme.Danger

@Composable
fun EditorScreen(
    state: EditorUiState,
    onPickVideo: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onTranscribe: () -> Unit,
    onWordDelete: (Int) -> Unit,
    onUndoDelete: () -> Unit,
    onClearDelete: () -> Unit,
    onExport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("VidEdit Android", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Text-based editor: whisper.cpp transcription + ffmpeg cut/export", style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPickVideo) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Text(" Pick Video")
            }
            OutlinedTextField(
                value = state.language,
                onValueChange = onLanguageChange,
                modifier = Modifier.weight(1f),
                label = { Text("Lang") },
                singleLine = true,
            )
            Button(onClick = onTranscribe, enabled = state.video != null && !state.isTranscribing) {
                Icon(Icons.Default.GraphicEq, contentDescription = null)
                Text(" Transcribe")
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Video: ${state.video?.displayName ?: "None"}")
                Text("Duration: ${state.video?.durationSec?.let { "%.1f".format(it) + "s" } ?: "-"}")
                Text("Deleted segments: ${state.deletedSegments.size}")
                state.lastExportPath?.let { Text("Last export: $it") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onUndoDelete, enabled = state.deletedSegments.isNotEmpty()) {
                Icon(Icons.Default.Undo, null)
                Text(" Undo")
            }
            TextButton(onClick = onClearDelete, enabled = state.deletedSegments.isNotEmpty()) {
                Icon(Icons.Default.DeleteSweep, null)
                Text(" Clear")
            }
            Button(onClick = onExport, enabled = state.video != null && !state.isExporting) {
                Icon(Icons.Default.ContentCut, null)
                Text(" Export")
            }
            if (state.isTranscribing || state.isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.statusMessage.contains("failed", ignoreCase = true)) Danger else MaterialTheme.colorScheme.onSurface,
        )

        Text("Transcript", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Words: ${state.transcript.size}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Marked deleted: ${countDeletedWords(state.transcript, state.deletedSegments)}",
                style = MaterialTheme.typography.bodySmall,
                color = Danger,
            )
        }
        TranscriptList(
            words = state.transcript,
            deletedSegments = state.deletedSegments,
            onWordClick = onWordDelete,
        )
    }
}

@Composable
private fun ColumnScope.TranscriptList(
    words: List<TranscriptWord>,
    deletedSegments: List<TimeSegment>,
    onWordClick: (Int) -> Unit,
) {
    if (words.isEmpty()) {
        Text("No transcript yet.", style = MaterialTheme.typography.bodyMedium)
        return
    }

    val deletedFlags = remember(words, deletedSegments) {
        words.map { word ->
            deletedSegments.any { segment ->
                word.end > segment.start && word.start < segment.end
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(words) { index, word ->
            val isDeleted = deletedFlags.getOrNull(index) == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isDeleted) Danger.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onWordClick(index) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "${index + 1}. ${word.word}",
                        color = if (isDeleted) Danger else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isDeleted) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    if (isDeleted) {
                        Text(
                            text = "Marked for deletion",
                            style = MaterialTheme.typography.labelSmall,
                            color = Danger,
                        )
                    }
                }
                Text(
                    text = "${"%.2f".format(word.start)}-${"%.2f".format(word.end)}s",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun countDeletedWords(words: List<TranscriptWord>, deletedSegments: List<TimeSegment>): Int {
    if (words.isEmpty() || deletedSegments.isEmpty()) return 0
    return words.count { word ->
        deletedSegments.any { segment ->
            word.end > segment.start && word.start < segment.end
        }
    }
}
