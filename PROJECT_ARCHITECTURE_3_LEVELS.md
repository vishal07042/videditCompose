# VidEdit Android: FFmpeg + whisper.cpp + Sync Architecture (3 Levels)

## 1) Top Overview

VidEdit is a text-driven video editor for Android.

- It transcribes speech from a selected video into timestamped words using `whisper.cpp`.
- The transcript is shown as clickable words; selecting words marks time ranges for deletion.
- Export uses `FFmpeg` to cut those marked segments out and render a new MP4.
- During playback, the currently spoken word is highlighted in real time.

Why this architecture:

- `whisper.cpp` provides on-device transcription (privacy, offline usage).
- `FFmpeg` is the most reliable way to do precise, timestamp-based media cuts.
- Word-level timestamps are the bridge between text edits and video timeline edits.

---

## 2) Detailed System Flow

### A. Input and Preparation

1. User picks a video from device storage.
2. App copies the source video to app cache for stable local processing.
3. App verifies required assets:
   - Whisper model (`ggml-tiny-q5_1.bin`)
   - Native whisper CLI shared object (`libwhisper_cli.so`)
   - FFmpeg runtime via Android FFmpeg kit library

### B. Transcription Pipeline (whisper.cpp)

1. FFmpeg extracts mono WAV audio (`16kHz`, PCM).
2. App runs whisper CLI with JSON output flags.
3. JSON parser normalizes output formats into a single `List<TranscriptWord>`:
   - `word`
   - `start` (seconds)
   - `end` (seconds)
   - `confidence`
4. Transcript is displayed in editor UI.

### C. Editing Model

1. Clicking words adds deletion segments (`start..end`) to a segment list.
2. Segment list is merged to avoid overlap duplication.
3. UI calculates:
   - deleted word count
   - kept duration
   - highlighted deleted text rows

### D. Playback Synchronization

1. Video player position is polled continuously.
2. Current playback time is matched against transcript word ranges.
3. Active word is highlighted and transcript auto-scrolls to keep context visible.
4. Timeline/waveform tap seeks playback directly.

### E. Export Pipeline (FFmpeg)

1. App computes `keptSegments = fullDuration - deletedSegments`.
2. FFmpeg `filter_complex` trims and concatenates kept ranges.
3. Output video is encoded and saved to public folder:
   - `/storage/emulated/0/videidit`
4. Fallback profiles handle device-specific option/codec incompatibilities.

---

## 3) High Technical Detail

### 3.1 Core Data Contracts

- `TranscriptWord(word: String, start: Double, end: Double, confidence: Double)`
- `TimeSegment(start: Double, end: Double)`
- `EditorUiState` holds transcript, deleted segments, player/export status, language, and status messages.

These contracts keep transcription, UI selection, and FFmpeg export decoupled but interoperable.

### 3.2 Whisper Execution and Parsing

Whisper command shape (conceptual):

```bash
libwhisper_cli.so -m <model.bin> -f <audio.wav> -l <lang> -oj -of <output_prefix>
```

Parser behavior:

- Accepts multiple possible JSON structures (`words`, `segments`, `transcription`, nested `result`).
- Handles token-based and text-based fallback paths.
- Normalizes timestamp units to seconds.
- Produces deterministic word timeline used by UI and export logic.

Why normalization matters:

- If timestamp units are wrong, word-delete ranges become too large.
- This causes false positives in deleted word counts and incorrect export cuts.

### 3.3 Segment Math and Deletion Semantics

- Word is treated as deleted if time ranges overlap:
  - `word.end > segment.start && word.start < segment.end`
- Multiple user deletions are merged to canonical non-overlapping segments.
- Export operates on kept ranges, not deleted ranges:
  - safer for FFmpeg concat graphs
  - avoids negative/empty spans

### 3.4 Playback Sync Logic

Active word resolution:

1. Exact match: `currentTime in [word.start, word.end]`
2. Near-gap fallback: nearest word within tolerance (for smoother scrub UX)

UI sync:

- active row highlight updates from player clock
- transcript list auto-scrolls to keep active row visible
- timeline click converts x-position ratio into target milliseconds and seeks player

### 3.5 FFmpeg Graph Strategy

High-level graph:

1. For each kept segment:
   - video: `trim + setpts`
   - audio: `atrim + asetpts` (if present)
2. Concatenate all kept clips in timeline order.
3. Map final outputs and encode.

Error handling strategy:

- First attempt: preferred encoding profile.
- Fallback: compatibility profile when option/encoder errors are detected.
- Additional fallback: video-only path for clips with missing/problematic audio streams.

### 3.6 Android Storage and Permission Model

Export target is public media-visible path:

- `/storage/emulated/0/videidit`

Permission handling:

- Android 11+ checks all-files-access flow where required by implementation.
- Legacy write permission path remains for older API levels.

### 3.7 Why This Design Is Practical

- On-device inference avoids server dependence.
- Word timestamps make editing intuitive for non-linear editors.
- FFmpeg handles final media integrity and codec constraints.
- UI synchronization gives immediate visual confidence that text edits map to real video time.

