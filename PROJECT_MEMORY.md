# Project Memory - Echo Audio Recorder

**Last Updated:** 2026-02-08

## Overview
This document serves as the central memory for the Echo project, tracking the current state of implementations, in-progress features, and technical decisions.

---

## Project Structure

```
echo/
├── SaidIt/                    # Main Android application module
│   ├── src/main/
│   │   ├── java/eu/mrogalski/saidit/
│   │   │   ├── export/        # Audio encoding implementations
│   │   │   ├── storage/       # Audio storage management
│   │   │   ├── analysis/      # Audio processing and segmentation
│   │   │   └── ml/           # Machine learning classifiers
│   │   └── res/
│   │       ├── layout/        # UI layouts
│   │       └── values/        # Resources (strings, etc.)
│   └── build.gradle.kts
├── core/                      # Core utilities
├── domain/                    # Domain logic
├── data/                      # Data layer
└── gradle/                    # Gradle configuration
```

---

## Feature Implementations

### ✅ Implemented Features

1. **Continuous Background Recording**
   - Service-based recording using `AudioRecord`
   - Circular buffer audio memory implementation (`AudioMemory`)
   - Notification for foreground service
   - Low power consumption with event-driven callbacks

2. **Manual Clip Export**
   - Export from memory buffer with duration selection
   - Currently supports WAV and AAC formats
   - Integration with MediaStore for file access

3. **Auto-Save Functionality**
   - Automatic saving when buffer reaches capacity
   - Configurable duration thresholds
   - Background export execution

4. **Audio Processing Pipeline**
   - Real-time audio analysis
   - Segmentation controller for managing audio chunks
   - Integration with ML classifiers

5. **Settings Management**
   - Audio quality selection (8kHz, 16kHz, 48kHz)
   - Memory usage controls (Low, Medium, High)
   - Audio effects (Noise Suppression, AGC)
   - Auto-save configuration

---

## 🚧 In-Progress Features

### Audio Export Feature (Multi-Format)

**Status:** 40% Complete

**Documentation:** See [`AUDIO_EXPORT_PROGRESS.md`](./AUDIO_EXPORT_PROGRESS.md) for detailed implementation status.

**Summary:**
- Adding support for MP3, Opus, and enhanced WAV export
- Real-time file size estimation in settings
- Per-recording format configuration

**Completed Components:**
- ✅ `AudioExporter` interface
- ✅ `OpusExporter` (MediaCodec-based)
- ✅ `Mp3Exporter` (LAME-based, JNI wrapper created)
- ✅ `WavExporter` (16/24/32-bit support)
- ✅ Settings UI for export configuration
- ✅ Size estimation logic

**Remaining Work:**
- ⏳ Update `RecordingExporter` to use new exporters
- ⏳ Integrate LAME native library files
- ⏳ Add per-recording settings in SaveClipBottomSheet
- ⏳ Update auto-save logic for new exporters
- ⏳ Comprehensive testing

---

## Technical Decisions

### Audio Memory Implementation
- **Choice:** Custom circular buffer using byte array
- **Rationale:** Efficient memory usage with O(1) read/write operations
- **Location:** `AudioMemory.java`

### Audio Recording
- **Choice:** `AudioRecord` with READ_NON_BLOCKING
- **Rationale:** Event-driven approach reduces power consumption vs. blocking reads
- **Thread:** Dedicated audio handler thread with priority `THREAD_PRIORITY_AUDIO`

### Audio Analysis
- **Choice:** Separate analysis handler thread
- **Rationale:** Decouples audio capture from ML inference, preventing recording interruptions
- **Frame Size:** 20ms chunks for optimal ML performance

### Export Formats
- **Current:** WAV, AAC
- **In Progress:** MP3 (LAME), Opus (MediaCodec), enhanced WAV (24/32-bit)
- **Future Consideration:** FLAC, Vorbis

---

## Known Issues & Limitations

### LAME Library Integration
- **Issue:** `Mp3Exporter` requires native `.so` files not currently in project
- **Impact:** MP3 export will fail at runtime
- **Solution Needed:** Compile or download libmp3lame for Android architectures

### API Level Constraints
- **Min SDK:** 30 (Android 11)
- **Impact:** `MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG` requires API 29 (OK)
- **Note:** Opus export fully supported

### Testing Coverage
- **Status:** Limited automated tests
- **Gap:** Integration tests for export functionality missing
- **Recommendation:** Add Espresso tests for UI flows

---

## Configuration Files

### SharedPreferences Keys
```java
// Memory & Quality
"AUDIO_MEMORY_ENABLED_KEY"      // Boolean - is background listening enabled
"AUDIO_MEMORY_SIZE_KEY"          // Long - max memory in bytes
"SAMPLE_RATE_KEY"                // Int - sample rate (8000, 16000, 48000)

// Audio Effects
"noise_suppressor_enabled"       // Boolean - noise suppression
"automatic_gain_control_enabled" // Boolean - AGC

// Auto-Save
"auto_save_enabled"              // Boolean
"auto_save_duration"             // Int - duration in seconds

// Export (New)
"export_format"                  // String - "wav", "mp3", "opus"
"export_bitrate"                 // Int - bitrate in bps
"export_bit_depth"               // Int - bit depth (16, 24, 32)
```

### Build Configuration
- **Min SDK:** 30
- **Target SDK:** 34
- **Compile SDK:** 34
- **Java Version:** 17
- **Kotlin:** 1.9.22

---

## Dependencies

### Core Libraries
- **Material Components:** com.google.android.material:material:1.11.0
- **Hilt:** com.google.dagger:hilt-android:2.50 (DI)
- **TapTargetView:** com.getkeepsafe.taptargetview:taptargetview:1.13.3 (Onboarding)

### Audio Processing
- **Custom WAV Library:** `simplesound` package (included in source)
- **Android Media Framework:** MediaCodec, MediaMuxer, AudioRecord

### Testing
- **JUnit:** 4.13.2
- **Mockito:** 5.11.0
- **Robolectric:** 4.10.3

---

## Code Conventions

### Package Structure
- `eu.mrogalski.saidit` - Main application package
- `eu.mrogalski.saidit.export` - Audio encoding
- `eu.mrogalski.saidit.storage` - Data persistence
- `eu.mrogalski.saidit.analysis` - Real-time processing
- `eu.mrogalski.saidit.ml` - Machine learning
- `eu.mrogalski.saidit.ui` - UI components (activities, fragments)

### Naming
- Activities: `*Activity.java`
- Services: `*Service.java`
- Exporters: `*Exporter.java` (implement `AudioExporter` interface)
- Managers: `*Manager.java`
- Utilities: `*Helper.java` or `*Utils.java`

### Threading Model
- **Main Thread:** UI operations only
- **Audio Thread:** `audioHandler` - AudioRecord operations
- **Analysis Thread:** `analysisHandler` - ML inference, segmentation
- **Background Tasks:** `analysisHandler` for heavy exports

---

## File References

### Key Files to Modify for Audio Export Feature
- `RecordingExporter.java` - Update to use AudioExporter factory
- `SaveClipBottomSheet.java` - Add per-recording settings UI
- Any auto-save worker file - Update to use new exporters

### Important Service Files
- `SaidItService.java` - Main background recording service
- `AudioProcessingPipeline.java` - Coordinates audio processing and storage
- `SegmentationController.java` - Manages audio segmentation

---

## Future Work & Roadmap

### High Priority
1. Complete Audio Export Feature
   - Integrate LAME native library
   - Update RecordingExporter
   - Add per-clip settings UI
   - Testing and validation

2. Improve Test Coverage
   - Unit tests for exporters
   - Integration tests for service
   - UI tests for settings

### Medium Priority
3. Enhanced Audio Processing
   - Additional ML models for event detection
   - Better segmentation algorithms
   - Audio effects library

4. Performance Optimization
   - Reduce memory footprint
   - Optimize battery usage
   - Faster export times

### Low Priority
5. Additional Export Formats
   - FLAC support
   - AAC VBR options
   - Custom format selection

6. UI/UX Improvements
   - Material You theming
   - More intuitive controls
   - Better visualizations

---

## Debugging Notes

### Common Issues

**Issue:** AudioRecord fails to initialize
- **Solution:** Ensure RECORD_AUDIO permission is granted
- **Check:** Device supports requested sample rate

**Issue:** Export fails with "File not found"
- **Solution:** Check cache directory permissions
- **Verify:** Export format is supported on device

**Issue:** Memory buffer fills quickly
- **Solution:** Reduce sample rate or memory size in settings
- **Check:** Not recording at unnecessarily high quality

### Logging Tags
- `SaidItService` - Main service operations
- `RecordingExporter` - Export operations
- `AudioMemory` - Memory management
- `AudioProcessingPipeline` - Analysis and segmentation

---

## Contact & Resources

- **GitHub Repository:** https://github.com/mafik/echo
- **Original Plan:** `feature_audio_export_plan.md`
- **Export Progress:** `AUDIO_EXPORT_PROGRESS.md`
- **Report Issues:** Create issue on GitHub

---

## Changelog

### 2026-02-08
- Created `AudioExporter` interface and implementations (Opus, MP3, WAV)
- Added export settings UI in SettingsActivity
- Implemented real-time size estimation
- Updated SaidItService to support new export parameters
- Created AUDIO_EXPORT_PROGRESS.md for detailed tracking

### Previous
- Implemented continuous background recording
- Added auto-save functionality
- Created audio processing pipeline with ML integration
- Built settings management system
