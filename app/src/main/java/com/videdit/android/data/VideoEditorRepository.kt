package com.videdit.android.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.videdit.android.core.SegmentUtils
import com.videdit.android.data.command.BinaryInstaller
import com.videdit.android.data.command.NativeCommandRunner
import com.videdit.android.data.ffmpeg.FfmpegService
import com.videdit.android.data.whisper.WhisperCppService
import com.videdit.android.model.ExportSettings
import com.videdit.android.model.TimeSegment
import com.videdit.android.model.TranscriptWord
import com.videdit.android.model.VideoFile
import java.io.File

class VideoEditorRepository(private val context: Context) {
    private val commandRunner = NativeCommandRunner()
    private val ffmpegService = FfmpegService()
    private val whisperModel = File(context.filesDir, "models/ggml-tiny-q5_1.bin")

    fun prepareBundledAssets() {
        val installer = BinaryInstaller(context)
        installer.installIfBundled("models/ggml-tiny-q5_1.bin", whisperModel)
    }

    fun transcriptionReadiness(): String? {
        val installer = BinaryInstaller(context)
        if (!whisperModel.exists()) {
            if (installer.isBundled("models/ggml-tiny-q5_1.bin")) {
                return "Failed to install whisper model to ${whisperModel.absolutePath}. Reinstall app."
            }
            return "Missing bundled model asset `app/src/main/assets/models/ggml-tiny-q5_1.bin`."
        }

        val whisperCli = whisperCliBinary()
        if (!whisperCli.exists()) {
            return "Missing native whisper binary at ${whisperCli.absolutePath}. Ensure jniLibs includes libwhisper_cli.so for device ABI."
        }

        val probe = runCatching { commandRunner.run(listOf(whisperCli.absolutePath, "--help")) }
        if (probe.isFailure) {
            return "Cannot execute native whisper binary at ${whisperCli.absolutePath}: ${probe.exceptionOrNull()?.message}"
        }

        return null
    }

    fun exportReadiness(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            return "Storage permission required. Allow 'All files access' for this app to export to /storage/emulated/0/videidit."
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val granted = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return "Storage permission required. Grant storage permission to export to /storage/emulated/0/videidit."
            }
        }

        val folder = exportFolder()
        if (!folder.exists() && !folder.mkdirs()) {
            return "Cannot create export folder at ${folder.absolutePath}."
        }
        return null
    }

    fun transcribe(video: VideoFile, language: String): List<TranscriptWord> {
        transcriptionReadiness()?.let { error(it) }
        val source = File(video.localPath)
        val wav = File(context.cacheDir, "audio_${System.currentTimeMillis()}.wav")
        ffmpegService.extractAudioToWav(source, wav)
        return whisperService().transcribe(wav, language)
    }

    fun export(
        video: VideoFile,
        deletedSegments: List<TimeSegment>,
        settings: ExportSettings,
    ): File {
        exportReadiness()?.let { error(it) }
        val source = File(video.localPath)
        val kept = SegmentUtils.keptSegments(video.durationSec, deletedSegments)
        require(kept.isNotEmpty()) { "No video content left after deletions." }

        val output = File(exportFolder(), "export_${System.currentTimeMillis()}.mp4")
        ffmpegService.cutVideo(source, output, kept, settings)
        return output
    }

    private fun whisperService() = WhisperCppService(
        commandRunner = commandRunner,
        whisperCliPath = whisperCliBinary().absolutePath,
        modelPath = whisperModel.absolutePath,
    )

    private fun whisperCliBinary(): File {
        return File(context.applicationInfo.nativeLibraryDir, "libwhisper_cli.so")
    }

    private fun exportFolder(): File {
        val root = Environment.getExternalStorageDirectory()
        return File(root, "videidit")
    }
}
