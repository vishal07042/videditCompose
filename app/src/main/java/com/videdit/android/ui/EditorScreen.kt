package com.videdit.android.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.videdit.android.model.TimeSegment
import com.videdit.android.model.TranscriptWord
import com.videdit.android.ui.theme.Danger
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

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
    val context = LocalContext.current
    val videoPath = state.video?.localPath
    val player = remember(videoPath) {
        videoPath?.let { path ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
                prepare()
                playWhenReady = false
            }
        }
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    var positionMs by remember(videoPath) { mutableLongStateOf(0L) }
    var durationMs by remember(videoPath) {
        mutableLongStateOf(((state.video?.durationSec ?: 0.0) * 1000.0).toLong())
    }
    var isSeeking by remember { mutableFloatStateOf(-1f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (true) {
            if (player != null) {
                val d = player.duration
                if (d > 0) durationMs = d
                positionMs = player.currentPosition.coerceAtLeast(0L)
                isPlaying = player.isPlaying
            }
            delay(120)
        }
    }

    val currentSec = positionMs / 1000.0
    val activeWordIndex = remember(state.transcript, currentSec) {
        findActiveWordIndex(state.transcript, currentSec)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeWordIndex) {
        if (activeWordIndex >= 0 && state.transcript.isNotEmpty()) {
            val target = max(0, activeWordIndex - 3)
            listState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF02061D),
                        Color(0xFF060B2D),
                    ),
                ),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (controlsVisible) {
                TopControls(
                    state = state,
                    onPickVideo = onPickVideo,
                    onLanguageChange = onLanguageChange,
                    onTranscribe = onTranscribe,
                    onUndoDelete = onUndoDelete,
                    onClearDelete = onClearDelete,
                    onExport = onExport,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text("Controls hidden", color = Color(0xFF8BA0E8), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { controlsVisible = !controlsVisible }) {
                Icon(
                    imageVector = if (controlsVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFFD0DBFF),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF0A1230)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                        ) {
                            if (player != null) {
                                AndroidView(
                                    factory = { context ->
                                        PlayerView(context).apply {
                                            this.player = player
                                            useController = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color(0xAAFFFFFF),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(56.dp)
                                        .clickable {
                                            if (player.isPlaying) player.pause() else player.play()
                                        },
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color(0xFF0A1230),
                                            modifier = Modifier.size(30.dp),
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Pick a video", color = Color.White)
                                }
                            }
                        }
                        Text(
                            text = state.video?.displayName ?: "No video loaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB7C2E8),
                        )
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF0A1230)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        TimelineWaveform(
                            durationMs = durationMs,
                            positionMs = positionMs,
                            deletedSegments = state.deletedSegments,
                            seed = state.video?.displayName?.hashCode() ?: 7,
                        )

                        val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                        val sliderValue = if (isSeeking >= 0f) isSeeking else positionMs.toFloat().coerceIn(0f, sliderMax)
                        Slider(
                            value = sliderValue,
                            onValueChange = { value ->
                                isSeeking = value
                            },
                            valueRange = 0f..sliderMax,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = Color(0xFF4D74FF),
                                activeTrackColor = Color(0xFF4D74FF),
                                inactiveTrackColor = Color(0xFF283058),
                            ),
                            onValueChangeFinished = {
                                if (player != null && isSeeking >= 0f) {
                                    player.seekTo(isSeeking.toLong())
                                    positionMs = isSeeking.toLong()
                                }
                                isSeeking = -1f
                            },
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(positionMs / 1000.0), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4D0F9))
                            Text(
                                text = "Deleted words: ${countDeletedWords(state.transcript, state.deletedSegments)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Danger,
                            )
                            Text(formatTime(durationMs / 1000.0), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4D0F9))
                        }
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF0A1230)),
            ) {
                TranscriptPanel(
                    words = state.transcript,
                    deletedSegments = state.deletedSegments,
                    activeWordIndex = activeWordIndex,
                    onWordClick = onWordDelete,
                    listState = listState,
                )
            }
        }

        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.statusMessage.contains("failed", ignoreCase = true)) Danger else Color(0xFF9FB0E8),
        )
    }
}

@Composable
private fun TopControls(
    state: EditorUiState,
    onPickVideo: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onTranscribe: () -> Unit,
    onUndoDelete: () -> Unit,
    onClearDelete: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(text = "Undo", icon = Icons.AutoMirrored.Filled.Undo, onClick = onUndoDelete, enabled = state.deletedSegments.isNotEmpty())
        PillButton(text = "Clear", icon = Icons.Default.DeleteSweep, onClick = onClearDelete, enabled = state.deletedSegments.isNotEmpty())
        Spacer(modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = state.language,
            onValueChange = onLanguageChange,
            modifier = Modifier.width(84.dp),
            singleLine = true,
            label = { Text("Lang") },
        )
        TextButton(onClick = onPickVideo) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Text(" Change Video")
        }
        Button(onClick = onTranscribe, enabled = state.video != null && !state.isTranscribing) {
            Icon(Icons.Default.GraphicEq, contentDescription = null)
            Text(" Transcribe")
        }
        PillButton(text = "Export", icon = Icons.Default.ContentCut, onClick = onExport, enabled = state.video != null && !state.isExporting)
        if (state.isTranscribing || state.isExporting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (enabled) Color(0xFF232B4C) else Color(0xFF1A2039),
        tonalElevation = 1.dp,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) Color(0xFFE7ECFF) else Color(0xFF8392C2), modifier = Modifier.size(18.dp))
            Text(text, color = if (enabled) Color(0xFFE7ECFF) else Color(0xFF8392C2), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TranscriptPanel(
    words: List<TranscriptWord>,
    deletedSegments: List<TimeSegment>,
    activeWordIndex: Int,
    onWordClick: (Int) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
    ) {
        Text("Transcript", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFE7ECFF))
        Spacer(modifier = Modifier.height(6.dp))
        if (words.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transcript yet.", color = Color(0xFF96A5D8))
            }
            return
        }

        val deletedFlags = remember(words, deletedSegments) {
            words.map { word ->
                deletedSegments.any { segment -> word.end > segment.start && word.start < segment.end }
            }
        }

        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(words) { index, word ->
                val isDeleted = deletedFlags.getOrNull(index) == true
                val isActive = index == activeWordIndex
                val rowColor = when {
                    isActive -> Color(0xFF1638B8)
                    isDeleted -> Danger.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(rowColor)
                        .clickable { onWordClick(index) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${index + 1}. ${word.word}",
                        color = if (isDeleted) Danger else Color(0xFFE8EDFF),
                        textDecoration = if (isDeleted) TextDecoration.LineThrough else TextDecoration.None,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        text = "${"%.2f".format(word.start)}-${"%.2f".format(word.end)}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9EB0E5),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineWaveform(
    durationMs: Long,
    positionMs: Long,
    deletedSegments: List<TimeSegment>,
    seed: Int,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF222948)),
    ) {
        val w = size.width
        val h = size.height
        val barCount = 180
        val step = w / barCount

        for (i in 0 until barCount) {
            val normalized = abs(sin((i + seed) * 0.37f)).toFloat()
            val barH = h * (0.10f + normalized * 0.65f)
            val x = i * step
            drawLine(
                color = Color(0xFF6D79B6).copy(alpha = 0.75f),
                start = Offset(x, h / 2f - barH / 2f),
                end = Offset(x, h / 2f + barH / 2f),
                strokeWidth = 1.5f,
            )
        }

        val totalSec = (durationMs / 1000.0).coerceAtLeast(0.001)
        deletedSegments.forEach { segment ->
            val x = ((segment.start / totalSec).toFloat() * w).coerceIn(0f, w)
            drawLine(
                color = Danger.copy(alpha = 0.8f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 2.5f,
            )
        }

        val playheadX = ((positionMs.toDouble() / durationMs.coerceAtLeast(1).toDouble()) * w).toFloat().coerceIn(0f, w)
        drawLine(
            color = Color(0xFF6F88FF),
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, h),
            strokeWidth = 2f,
        )
    }
}

private fun findActiveWordIndex(words: List<TranscriptWord>, currentSec: Double): Int {
    if (words.isEmpty()) return -1
    val exact = words.indexOfFirst { word -> currentSec >= word.start && currentSec <= word.end }
    if (exact >= 0) return exact

    // If between words while scrubbing, keep nearest neighbor highlighted for smooth UX.
    val nearest = words.minByOrNull { word ->
        when {
            currentSec < word.start -> word.start - currentSec
            currentSec > word.end -> currentSec - word.end
            else -> 0.0
        }
    } ?: return -1
    val distance = min(abs(nearest.start - currentSec), abs(nearest.end - currentSec))
    if (distance <= 0.45) return words.indexOf(nearest)
    return -1
}

private fun countDeletedWords(words: List<TranscriptWord>, deletedSegments: List<TimeSegment>): Int {
    if (words.isEmpty() || deletedSegments.isEmpty()) return 0
    return words.count { word ->
        deletedSegments.any { segment -> word.end > segment.start && word.start < segment.end }
    }
}

private fun formatTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val mins = total / 60
    val secs = total % 60
    return "%02d:%02d".format(mins, secs)
}
