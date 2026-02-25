package com.videdit.android.data.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.videdit.android.model.ExportSettings
import com.videdit.android.model.TimeSegment
import java.io.File

class FfmpegService {

    fun extractAudioToWav(inputVideo: File, outputWav: File) {
        val args = listOf(
            "-y",
            "-i",
            inputVideo.absolutePath,
            "-vn",
            "-c:a",
            "pcm_s16le",
            "-ar",
            "16000",
            "-ac",
            "1",
            outputWav.absolutePath,
        )

        runOrThrow(args, "ffmpeg audio extraction")
    }

    fun cutVideo(inputVideo: File, outputVideo: File, keepSegments: List<TimeSegment>, settings: ExportSettings) {
        require(keepSegments.isNotEmpty()) { "No keep segments provided." }

        val withAudio = buildCutArgs(
            inputVideo = inputVideo,
            outputVideo = outputVideo,
            keepSegments = keepSegments,
            settings = settings,
            includeAudio = true,
            compatibilityMode = false,
        )
        val firstAttempt = execute(withAudio)
        if (firstAttempt.success) return

        if (looksLikeMissingAudio(firstAttempt.rawDetails)) {
            val videoOnly = buildCutArgs(
                inputVideo = inputVideo,
                outputVideo = outputVideo,
                keepSegments = keepSegments,
                settings = settings,
                includeAudio = false,
                compatibilityMode = false,
            )
            val fallbackAttempt = execute(videoOnly)
            if (fallbackAttempt.success) return
            if (looksLikeUnsupportedOptionOrEncoder(fallbackAttempt.rawDetails)) {
                val compatVideoOnly = buildCutArgs(
                    inputVideo = inputVideo,
                    outputVideo = outputVideo,
                    keepSegments = keepSegments,
                    settings = settings,
                    includeAudio = false,
                    compatibilityMode = true,
                )
                val compatVideoOnlyAttempt = execute(compatVideoOnly)
                if (compatVideoOnlyAttempt.success) return
                error("ffmpeg cut/export failed (video-only compatibility fallback): ${compatVideoOnlyAttempt.details}")
            }
            error("ffmpeg cut/export failed (video-only fallback): ${fallbackAttempt.details}")
        }

        if (looksLikeUnsupportedOptionOrEncoder(firstAttempt.rawDetails)) {
            val compatWithAudio = buildCutArgs(
                inputVideo = inputVideo,
                outputVideo = outputVideo,
                keepSegments = keepSegments,
                settings = settings,
                includeAudio = true,
                compatibilityMode = true,
            )
            val compatAttempt = execute(compatWithAudio)
            if (compatAttempt.success) return
            if (looksLikeMissingAudio(compatAttempt.rawDetails)) {
                val compatVideoOnly = buildCutArgs(
                    inputVideo = inputVideo,
                    outputVideo = outputVideo,
                    keepSegments = keepSegments,
                    settings = settings,
                    includeAudio = false,
                    compatibilityMode = true,
                )
                val compatVideoOnlyAttempt = execute(compatVideoOnly)
                if (compatVideoOnlyAttempt.success) return
                error("ffmpeg cut/export failed (compatibility video-only fallback): ${compatVideoOnlyAttempt.details}")
            }
            error("ffmpeg cut/export failed (compatibility fallback): ${compatAttempt.details}")
        }

        error("ffmpeg cut/export failed: ${firstAttempt.details}")
    }

    private fun buildCutArgs(
        inputVideo: File,
        outputVideo: File,
        keepSegments: List<TimeSegment>,
        settings: ExportSettings,
        includeAudio: Boolean,
        compatibilityMode: Boolean,
    ): MutableList<String> {
        val filterParts = mutableListOf<String>()
        val concatInputs = mutableListOf<String>()

        keepSegments.forEachIndexed { index, segment ->
            filterParts += "[0:v]trim=start=${segment.start}:end=${segment.end},setpts=PTS-STARTPTS[v$index]"
            if (includeAudio) {
                filterParts += "[0:a]atrim=start=${segment.start}:end=${segment.end},asetpts=PTS-STARTPTS[a$index]"
                concatInputs += "[v$index][a$index]"
            } else {
                concatInputs += "[v$index]"
            }
        }

        filterParts += if (includeAudio) {
            "${concatInputs.joinToString("")}concat=n=${keepSegments.size}:v=1:a=1[outv][outa]"
        } else {
            "${concatInputs.joinToString("")}concat=n=${keepSegments.size}:v=1:a=0[outv]"
        }

        val videoLabel = if (settings.scaleFilter != null) {
            filterParts += "[outv]${settings.scaleFilter}[vscaled]"
            "[vscaled]"
        } else {
            "[outv]"
        }
        val filterComplex = filterParts.joinToString(";")

        val args = mutableListOf(
            "-y",
            "-i",
            inputVideo.absolutePath,
            "-filter_complex",
            filterComplex,
            "-map",
            videoLabel,
        )

        if (compatibilityMode) {
            args.addAll(
                listOf(
                    "-c:v",
                    "mpeg4",
                    "-q:v",
                    "3",
                ),
            )
        } else {
            args.addAll(
                listOf(
                    "-c:v",
                    "libx264",
                    "-preset",
                    settings.preset,
                    "-crf",
                    settings.crf,
                    "-movflags",
                    "+faststart",
                ),
            )
        }

        if (includeAudio) {
            args.addAll(
                listOf(
                    "-map",
                    "[outa]",
                    "-c:a",
                    "aac",
                    "-b:a",
                    settings.audioBitrate,
                ),
            )
        }

        args += outputVideo.absolutePath
        return args
    }

    private fun execute(args: List<String>): ExecutionResult {
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        val rawDetails = session.allLogsAsString.ifBlank { session.failStackTrace ?: "Unknown ffmpeg error" }
        val details = compactError(rawDetails)
        return if (ReturnCode.isSuccess(session.returnCode)) {
            ExecutionResult(success = true, details = details, rawDetails = rawDetails)
        } else {
            ExecutionResult(success = false, details = details, rawDetails = rawDetails)
        }
    }

    private fun runOrThrow(args: List<String>, operation: String) {
        val result = execute(args)
        if (!result.success) {
            error("$operation failed: ${result.details}")
        }
    }

    private fun looksLikeMissingAudio(details: String): Boolean {
        val text = details.lowercase()
        return text.contains("stream specifier ':a'") ||
            text.contains("matches no streams") ||
            text.contains("stream map '0:a'") ||
            text.contains("cannot find a matching stream for unlabeled input pad")
    }

    private fun looksLikeUnsupportedOptionOrEncoder(details: String): Boolean {
        val text = details.lowercase()
        return text.contains("error splitting the argument list") ||
            text.contains("option not found") ||
            text.contains("unrecognized option") ||
            text.contains("unknown encoder")
    }

    private fun compactError(source: String): String {
        val lines = source
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val keywords = listOf(
            "error",
            "invalid",
            "failed",
            "cannot",
            "unable",
            "no such file",
            "not found",
            "permission denied",
            "matches no streams",
            "unrecognized option",
            "unknown encoder",
            "error splitting the argument list",
        )

        val focused = lines.filter { line ->
            val lower = line.lowercase()
            keywords.any { lower.contains(it) }
        }

        val selected = (if (focused.isNotEmpty()) focused else lines).takeLast(6)
        val joined = selected.joinToString(" | ")
        return if (joined.length > 600) joined.take(600) + "..." else joined
    }

    private data class ExecutionResult(
        val success: Boolean,
        val details: String,
        val rawDetails: String,
    )
}
