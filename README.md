# VidEdit Jetpack Compose (Android)

Core Android port of your text-based video editor:

- Video selection from storage
- Audio extraction with `ffmpeg`
- Transcription with `whisper.cpp` CLI + model
- Word-based cut marks (delete words -> delete time segments)
- Export edited MP4 with `ffmpeg`

## Current scope

This is the **core pipeline** implementation (not full polished editor UI):

1. Pick video
2. Transcribe
3. Tap words to mark cuts
4. Undo/Clear cuts
5. Export edited video

## Project path

`VidEditJetpackCompose/`

## Binary/model requirements

The app uses FFmpeg via Android SDK (FFmpegKit), so no standalone `ffmpeg` binary is required.
Whisper CLI is packaged as native libs in the APK:

- `app/src/main/jniLibs/arm64-v8a/libwhisper_cli.so`
- `app/src/main/jniLibs/armeabi-v7a/libwhisper_cli.so`

The app expects this model in app internal storage:
- `files/models/ggml-tiny-q5_1.bin`

At startup it copies model from bundled assets:

- `app/src/main/assets/models/ggml-tiny-q5_1.bin`

If not found, the app launches but transcription/export will be blocked with a setup error message.

Important:
- `libwhisper_cli.so` must be an Android Linux executable for each supported device ABI.
- After adding/replacing model in assets, reinstall the app so startup extraction runs again.

Bundled now:
- `app/src/main/jniLibs/arm64-v8a/libwhisper_cli.so`
- `app/src/main/jniLibs/armeabi-v7a/libwhisper_cli.so`
- `app/src/main/assets/models/ggml-tiny-q5_1.bin` is included in this project.

## Notes

- `whisper.cpp` command uses JSON output (`-oj`) and parses word/segment/token formats.
- `ffmpeg` export uses trim+concat filter graph from kept segments.
- Export output goes to app external files directory with timestamped filename.
