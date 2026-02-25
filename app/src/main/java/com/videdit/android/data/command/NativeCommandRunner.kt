package com.videdit.android.data.command

import java.io.File

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

class NativeCommandRunner {
    fun run(command: List<String>, workingDir: File? = null): CommandResult {
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(false)

        // Allow loading colocated shared libs (for example libc++_shared.so for ffmpeg builds).
        val binaryDir = command.firstOrNull()?.let { File(it).parentFile }?.absolutePath
        if (!binaryDir.isNullOrBlank()) {
            val env = process.environment()
            val existing = env["LD_LIBRARY_PATH"]
            env["LD_LIBRARY_PATH"] = if (existing.isNullOrBlank()) binaryDir else "$binaryDir:$existing"
        }

        val started = process.start()

        val stdout = started.inputStream.bufferedReader().use { it.readText() }
        val stderr = started.errorStream.bufferedReader().use { it.readText() }
        val exitCode = started.waitFor()

        return CommandResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
    }
}
