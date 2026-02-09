# Audio Export Feature Implementation Progress

**Last Updated:** 2026-02-08

## Overview
This document tracks the implementation progress of the multi-format audio export feature (MP3, Opus, WAV) with real-time size estimation and per-clip settings.

---

## Original Plan Summary

### 1. Research & Dependency Setup
- [x] **MP3 (LAME):** Identify and integrate the LAME encoder
- [ ] Verify native library presence in jniLibs
- [x] **Opus:** Verify native MediaCodec and MediaMuxer support (API 29+)
- [x] **Project Structure:** NDK configuration considered

### 2. Core Encoding Engine
- [x] **`AudioExporter` Interface:** Create a unified interface for all exporters
- [x] **`Mp3Exporter`:** Implement using the LAME library (with JNI wrapper)
- [x] **`OpusExporter`:** Implement using MediaCodec and MediaMuxer (Ogg container)
- [x] **`WavExporter` Upgrade:** Update existing WAV logic to support 24-bit and 32-bit float depths
- [x] **Size Estimation Utility:** Logic to calculate bytes-per-second

### 3. Settings UI Overhaul
- [x] **Global Settings (`SettingsActivity`):**
  - [x] Add "Auto-save Defaults" section
  - [x] Format selector (Toggle group: WAV, MP3, Opus)
  - [x] Bitrate slider (8kbps - 64kbps) for MP3/Opus
  - [x] Bit-depth selector for WAV (16, 24, 32)
  - [x] Real-time "Estimated Size per Minute" display
- [ ] **Manual Save Modal (`SaveClipBottomSheet`):**
  - [ ] Enhance bottom sheet for format/settings selection
  - [ ] Pre-fill with global defaults
  - [ ] Allow per-recording overrides

### 4. Service & Data Integration
- [x] **Intent Updates:** Pass `bitrate` and `bitDepth` through intents
- [ ] **`RecordingExporter` Update:** Use the AudioExporter factory
- [ ] **Auto-save Logic:** Ensure auto-save worker reads latest defaults

### 5. Verification
- [ ] Test all formats (WAV, MP3, Opus)
- [ ] Verify metadata and container integrity
- [ ] Validate size estimations

---

## Detailed Implementation Status

### ✅ Completed

#### 1. Core Encoding Infrastructure
Created the following files in `/SaidIt/src/main/java/eu/mrogalski/saidit/export/`:

- **AudioExporter.java** - Interface defining the contract for all audio exporters
  ```java
  public interface AudioExporter {
      void export(File pcmFile, File outputFile, int sampleRate, int channels, int bitRate) throws IOException;
  }
  ```

- **OpusExporter.java** - Opus encoder implementation using MediaCodec
  - Uses `MediaCodec` with `MIMETYPE_AUDIO_OPUS`
  - Uses `MediaMuxer` with `MUXER_OUTPUT_OGG` (API 29+)
  - Configurable bitrate
  - Proper buffer management and EOF handling

- **Mp3Exporter.java** - MP3 encoder using LAME
  - JNI wrapper calls to LAME native library
  - Converts 16-bit PCM to MP3
  - Configurable bitrate (32-320 kbps)
  - Proper flush and cleanup

- **WavExporter.java** - Enhanced WAV exporter
  - Supports 16-bit, 24-bit, and 32-bit float formats
  - Proper WAV header generation
  - Sample conversion for 24-bit and 32-bit float
  - Configurable bit depth

- **Lame.java** - JNI wrapper for LAME encoder
  - Native method declarations for LAME encoder
  - Methods: `init()`, `encode()`, `flush()`, `close()`
  - Library loading with error handling

#### 2. Settings UI Components

**strings.xml Updates** (`/SaidIt/src/main/res/values/strings.xml`):
- `export_settings_title`
- `export_format_title`
- `export_bitrate_title`
- `export_bit_depth_title`
- `estimated_size_per_minute`

**activity_settings.xml Layout Updates**:
- Export Settings Card with:
  - Format toggle group (WAV, MP3, Opus)
  - Bitrate slider (visible for MP3/Opus)
  - Bit depth toggle group (visible for WAV)
  - Estimated size display

**SettingsActivity.java Updates**:
- Added UI components for export settings
- Implemented format toggle listener
- Implemented bitrate slider listener with value display
- Implemented bit depth toggle listener
- Added `updateExportSettingsUI()` method to show/hide controls based on format
- Added `updateEstimatedSize()` method to calculate and display file size estimates
- Size calculation logic:
  - WAV: `sampleRate * channels * (bitDepth / 8) * 60`
  - MP3/Opus: `bitrate / 8 * 60`

#### 3. Service Integration Updates

**SaidItService.java Updates**:
- Added intent extras:
  - `EXTRA_BITRATE`
  - `EXTRA_BIT_DEPTH`
- Updated `exportRecording()` method signature to accept bitrate and bit depth
- Updated `ACTION_EXPORT_RECORDING` handling to pass new extras

---

### 🚧 In Progress / Partially Complete

#### RecordingExporter Update
The `RecordingExporter` class needs to be updated to:
1. Read export settings from SharedPreferences
2. Create appropriate AudioExporter instance
3. Pass correct parameters (bitrate/bitDepth) to the exporter

Current state: Uses hardcoded AAC format at 96 kbps

---

### ❌ Not Started

#### 1. LAME Native Library Integration
The `Mp3Exporter` requires native LAME library files:
- Need to add compiled `.so` files to `app/src/main/jniLibs/` for each architecture
- Alternatively, implement pre-compiled library integration

#### 2. Manual Save Modal (SaveClipBottomSheet)
Need to locate and enhance the manual save dialog:
- Add format selection dropdown
- Add bitrate/bit depth controls
- Pre-fill with global defaults
- Save per-recording overrides

#### 3. Auto-save Logic Integration
Update auto-save worker to:
- Read latest export settings from SharedPreferences
- Use new AudioExporter implementations
- Pass appropriate parameters

#### 4. Testing & Verification
- Unit tests for each exporter
- Integration tests with UI
- Manual testing of export functionality
- File integrity verification
- Size estimation accuracy testing

---

## Next Steps

1. **Immediate (High Priority)**
   - Update `RecordingExporter.export()` to use new AudioExporter implementations
   - Add SharedPreferences reading for export settings
   - Implement AudioExporter factory pattern

2. **Short Term (Medium Priority)**
   - Integrate LAME native library (compile or download pre-built)
   - Update auto-save logic to use new exporters
   - Add manual save modal enhancements

3. **Long Term (Low Priority)**
   - Comprehensive testing
   - Performance optimization
   - Additional format support (FLAC, AAC)

---

## Known Issues / Considerations

1. **LAME Library**: The Mp3Exporter will fail without native library files. Need to source or compile libmp3lame.so for Android (arm64-v8a, armeabi-v7a, x86, x86_64).

2. **API Level**: OpusExporter requires API 29+ for `MUXER_OUTPUT_OGG`. The app's `minSdk` is 30, so this is not an issue.

3. **File Size Estimation**: Current estimation is approximate and may differ from actual file sizes due to:
   - VBR encoding (if implemented)
   - Container overhead
   - Metadata

4. **Error Handling**: Need to ensure proper error handling and user feedback when:
   - Selected format is not available
   - Encoding fails
   - File write fails

---

## File Changes Summary

### New Files Created:
- `SaidIt/src/main/java/eu/mrogalski/saidit/export/AudioExporter.java`
- `SaidIt/src/main/java/eu/mrogalski/saidit/export/OpusExporter.java`
- `SaidIt/src/main/java/eu/mrogalski/saidit/export/Mp3Exporter.java`
- `SaidIt/src/main/java/eu/mrogalski/saidit/export/WavExporter.java`
- `SaidIt/src/main/java/eu/mrogalski/saidit/export/Lame.java`

### Modified Files:
- `SaidIt/src/main/res/values/strings.xml` - Added export settings strings
- `SaidIt/src/main/res/layout/activity_settings.xml` - Added export settings card
- `SaidIt/src/main/java/eu/mrogalski/saidit/SettingsActivity.java` - Added export settings UI logic
- `SaidIt/src/main/java/eu/mrogalski/saidit/SaidItService.java` - Added intent extras for bitrate/bitDepth

### Files to Modify:
- `SaidIt/src/main/java/eu/mrogalski/saidit/RecordingExporter.java` - Update to use AudioExporter
- Any file containing SaveClipBottomSheet - Add per-clip settings UI
- Any auto-save related files - Update to use new exporters

---

## Progress Percentage

**Overall Progress: ~40%**

- Section 1 (Research & Dependencies): 70%
- Section 2 (Core Encoding): 90%
- Section 3 (Settings UI): 50%
- Section 4 (Service Integration): 30%
- Section 5 (Verification): 0%
