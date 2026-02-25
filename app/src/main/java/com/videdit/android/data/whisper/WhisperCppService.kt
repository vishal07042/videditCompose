package com.videdit.android.data.whisper

import com.videdit.android.model.TranscriptWord
import com.videdit.android.data.command.NativeCommandRunner
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WhisperCppService(
    private val commandRunner: NativeCommandRunner,
    private val whisperCliPath: String,
    private val modelPath: String,
) {

    fun transcribe(audioWav: File, language: String = "en"): List<TranscriptWord> {
        val outputPrefix = File(audioWav.parentFile, "transcript_${System.currentTimeMillis()}")

        val command = listOf(
            whisperCliPath,
            "-m",
            modelPath,
            "-f",
            audioWav.absolutePath,
            "-l",
            language,
            "-oj",
            "-of",
            outputPrefix.absolutePath,
        )

        val result = commandRunner.run(command)
        if (result.exitCode != 0) {
            error("whisper transcription failed: ${result.stderr.ifBlank { result.stdout }}")
        }

        val jsonFile = File("${outputPrefix.absolutePath}.json")
        if (!jsonFile.exists()) {
            error("Whisper output JSON not found at ${jsonFile.absolutePath}")
        }

        val payload = JSONObject(jsonFile.readText())
        return parseAnyWhisperPayload(payload)
    }

    private fun parseAnyWhisperPayload(json: JSONObject): List<TranscriptWord> {
        val fromWordArray = parseWordArray(json)
        if (fromWordArray.isNotEmpty()) return fromWordArray

        val fromSegments = parseSegments(json)
        if (fromSegments.isNotEmpty()) return fromSegments

        val fromTranscriptionArray = parseTranscriptionArray(json)
        if (fromTranscriptionArray.isNotEmpty()) return fromTranscriptionArray

        val nestedResult = json.optJSONObject("result")
        if (nestedResult != null) {
            val fromNestedResult = parseAnyWhisperPayload(nestedResult)
            if (fromNestedResult.isNotEmpty()) return fromNestedResult
        }

        val fromText = parseTaggedText(json.optString("text", ""))
        if (fromText.isNotEmpty()) return fromText

        return emptyList()
    }

    private fun parseWordArray(json: JSONObject): List<TranscriptWord> {
        val words = json.optJSONArray("words") ?: return emptyList()
        val output = mutableListOf<TranscriptWord>()
        for (i in 0 until words.length()) {
            val item = words.optJSONObject(i) ?: continue
            val word = item.optString("word").trim()
            if (word.isEmpty()) continue
            output += TranscriptWord(
                word = word,
                start = item.optDouble("start", 0.0),
                end = item.optDouble("end", 0.0),
                confidence = item.optDouble("confidence", item.optDouble("probability", 1.0)),
            )
        }
        return output
    }

    private fun parseSegments(json: JSONObject): List<TranscriptWord> {
        val output = mutableListOf<TranscriptWord>()
        val segments = json.optJSONArray("segments") ?: return emptyList()

        for (i in 0 until segments.length()) {
            val segment = segments.optJSONObject(i) ?: continue
            val tokens = segment.optJSONArray("tokens")
            if (tokens != null && tokens.length() > 0) {
                output += parseTokenWords(tokens)
            } else {
                output += interpolateFromSegmentText(segment)
            }
        }

        return output
    }

    private fun parseTokenWords(tokens: JSONArray): List<TranscriptWord> {
        val output = mutableListOf<TranscriptWord>()
        for (i in 0 until tokens.length()) {
            val token = tokens.optJSONObject(i) ?: continue
            val text = token.optString("text").replace(Regex("\\[_.*?]"), "").trim()
            if (text.isEmpty()) continue
            val t0 = token.optDouble("t0", -1.0)
            val t1 = token.optDouble("t1", -1.0)
            if (t0 < 0 || t1 < 0) continue

            output += TranscriptWord(
                word = text,
                start = t0 / 100.0,
                end = t1 / 100.0,
                confidence = token.optDouble("p", 0.9),
            )
        }
        return output
    }

    private fun interpolateFromSegmentText(segment: JSONObject): List<TranscriptWord> {
        val text = segment.optString("text").replace(Regex("\\[_.*?]"), "").trim()
        if (text.isEmpty()) return emptyList()

        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val start = segment.optDouble("start", segment.optDouble("t0", 0.0) / 100.0)
        val end = segment.optDouble("end", segment.optDouble("t1", 0.0) / 100.0)
        val duration = if (end > start) end - start else 0.5
        val perWord = duration / words.size

        return words.mapIndexed { index, word ->
            TranscriptWord(
                word = word,
                start = start + index * perWord,
                end = start + (index + 1) * perWord,
                confidence = 0.7,
            )
        }
    }

    private fun parseTaggedText(text: String): List<TranscriptWord> {
        if (!text.contains("[_TT_")) return emptyList()
        val regex = Regex("\\[_TT_(\\d+)]")
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        val output = mutableListOf<TranscriptWord>()
        var lastIndex = 0
        var lastTime = 0.0

        for (match in matches) {
            val currentTime = (match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: 0.0) / 100.0
            val chunk = text.substring(lastIndex, match.range.first).trim()
            if (chunk.isNotEmpty()) {
                val words = chunk.replace(Regex("\\[_.*?]"), "").split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.isNotEmpty()) {
                    val duration = if (currentTime > lastTime) currentTime - lastTime else 0.1
                    val perWord = duration / words.size
                    words.forEachIndexed { index, word ->
                        output += TranscriptWord(
                            word = word,
                            start = lastTime + index * perWord,
                            end = lastTime + (index + 1) * perWord,
                            confidence = 0.85,
                        )
                    }
                }
            }
            lastTime = currentTime
            lastIndex = match.range.last + 1
        }

        return output
    }

    private fun parseTranscriptionArray(json: JSONObject): List<TranscriptWord> {
        val transcription = json.optJSONArray("transcription") ?: return emptyList()
        val output = mutableListOf<TranscriptWord>()

        for (i in 0 until transcription.length()) {
            val entry = transcription.optJSONObject(i) ?: continue
            val text = entry.optString("text").replace(Regex("\\[_.*?]"), "").trim()
            if (text.isEmpty()) continue

            val offsets = entry.optJSONObject("offsets")
            val timestamps = entry.optJSONObject("timestamps")

            val start = when {
                offsets != null -> readTime(offsets, "from")
                entry.has("start") -> entry.optDouble("start", 0.0)
                timestamps != null -> parseClockTime(timestamps.optString("from"))
                else -> 0.0
            }

            val end = when {
                offsets != null -> readTime(offsets, "to")
                entry.has("end") -> entry.optDouble("end", start + 0.4)
                timestamps != null -> parseClockTime(timestamps.optString("to"))
                else -> start + 0.4
            }

            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) continue

            val duration = if (end > start) end - start else 0.4
            val perWord = duration / words.size
            words.forEachIndexed { index, word ->
                output += TranscriptWord(
                    word = word,
                    start = start + index * perWord,
                    end = start + (index + 1) * perWord,
                    confidence = 0.8,
                )
            }
        }

        return output
    }

    private fun readTime(container: JSONObject, key: String): Double {
        val value = container.opt(key) ?: return 0.0
        return when (value) {
            is Number -> {
                normalizeOffsetSeconds(value.toDouble())
            }
            is String -> {
                val numeric = value.toDoubleOrNull()
                if (numeric != null) normalizeOffsetSeconds(numeric) else parseClockTime(value)
            }
            else -> 0.0
        }
    }

    private fun normalizeOffsetSeconds(raw: Double): Double {
        if (raw <= 0.0) return 0.0
        // Whisper JSON "offsets" are milliseconds in common CLI output.
        return raw / 1000.0
    }

    private fun parseClockTime(value: String): Double {
        val parts = value.trim().split(":")
        if (parts.size != 3) return value.toDoubleOrNull() ?: 0.0

        val hours = parts[0].toDoubleOrNull() ?: 0.0
        val minutes = parts[1].toDoubleOrNull() ?: 0.0
        val seconds = parts[2].replace(',', '.').toDoubleOrNull() ?: 0.0
        return hours * 3600 + minutes * 60 + seconds
    }
}
