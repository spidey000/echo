# Echo - Never Miss a Moment

Echo is a modern Android application that continuously records audio in the background, allowing you to go back in time and save moments that have already happened. Whether it's a brilliant idea, a funny quote, or an important note, Echo ensures you never miss it.

## Features

*   **Continuous Background Recording:** Echo runs silently in the background, keeping a rolling buffer of the last few hours of audio.
*   **Save Clips from the Past:** Instantly save audio clips of various lengths from the buffered memory.
*   **Auto-Save:** Automatically save recordings when the memory buffer is full, ensuring you never lose important audio.
*   **Modern, Intuitive Interface:** A clean, professional design built with Material You principles.
*   **Customizable Settings:** Adjust the audio quality and memory usage to fit your needs.

## Getting Started

### Prerequisites

*   Android Studio
*   Java Development Kit (JDK)

### Building the Project

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/mafik/echo.git
    ```
2.  **Open the project in Android Studio.**
3.  **Create a `local.properties` file** in the root of the project and add the following line, pointing to your Android SDK location:
    ```
    sdk.dir=/path/to/your/android/sdk
    ```
4.  **Build the project:**
    *   From the command line, run:
        ```bash
        ./gradlew assembleDebug
        ```
    *   Or, use the "Build" menu in Android Studio.

## Contributing

We welcome contributions! Please feel free to open an issue or submit a pull request.

## Project Documentation

- **Project Memory:** See [PROJECT_MEMORY.md](PROJECT_MEMORY.md) for current state of implementations, technical decisions, and in-progress features
- **Feature Plans:** See [feature_audio_export_plan.md](feature_audio_export_plan.md) for audio export feature implementation plan
- **Progress Tracking:** See [AUDIO_EXPORT_PROGRESS.md](AUDIO_EXPORT_PROGRESS.md) for detailed implementation status of audio export feature

## Future Development

For a detailed roadmap of planned features and improvements, please see [PROJECT_MEMORY.md](PROJECT_MEMORY.md).
