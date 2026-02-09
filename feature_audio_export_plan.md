# Echo Audio Export Feature Plan
**Timestamp:** 2026-02-08 14:30:00

This plan outlines the implementation of multi-format audio export (MP3, Opus, WAV) with real-time size estimation and per-clip settings.

## 1. Research & Dependency Setup
- **MP3 (LAME):** Identify and integrate the LAME encoder. Preference for a lightweight JNI/NDK implementation or a stable pre-compiled library.
- **Opus:** Verify native `MediaCodec` and `MediaMuxer` support for Ogg/Opus (API 29+).
- **Project Structure:** Ensure NDK is configured if a custom LAME build is required.

## 2. Core Encoding Engine
- **`AudioExporter` Interface:** Create a unified interface for all exporters.
- **`Mp3Exporter`:** Implement using the LAME library.
- **`OpusExporter`:** Implement using `MediaCodec` (Encoder) and `MediaMuxer` (Ogg container).
- **`WavExporter` Upgrade:** Update existing WAV logic to support 24-bit and 32-bit float depths.
- **Size Estimation Utility:** Logic to calculate bytes-per-second based on `(sampleRate * bitrate/depth)`.

## 3. Settings UI Overhaul
- **Global Settings (`SettingsActivity`):**
    - Add "Auto-save Defaults" section.
    - Format selector (Spinner/Toggle).
    - Bitrate slider (8kbps - 64kbps) for MP3/Opus.
    - Bit-depth selector for WAV.
    - Real-time "Estimated Size per Minute" display.
- **Manual Save Modal (`SaveClipBottomSheet`):**
    - Enhance the bottom sheet to allow format/settings selection.
    - Pre-fill with global defaults from `SharedPreferences`.
    - Allow per-recording overrides.

## 4. Service & Data Integration
- **Intent Updates:** Pass `format`, `bitrate`, and `bitDepth` through `ACTION_EXPORT_RECORDING` intents.
- **`SaidItService`:** Update the export task to use the `AudioExporter` factory.
- **Auto-save Logic:** Ensure the auto-save worker reads the latest global defaults.

## 5. Verification
- Test all formats (WAV, MP3, Opus).
- Verify metadata and container integrity (Ogg/Opus, MP3 VBR/CBR).
- Validate size estimations against actual file outputs.
