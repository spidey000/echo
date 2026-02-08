package eu.mrogalski.saidit.storage;

import java.io.File;
import java.io.IOException;

public interface RecordingStoreManager {
    /**
     * Called when a new segment begins.
     * @param timestamp The start time of the segment.
     */
    void onSegmentStart(long timestamp) throws IOException;

    /**
     * Called when a segment ends.
     * @param timestamp The end time of the segment.
     */
    void onSegmentEnd(long timestamp);

    /**
     * Appends audio data to the current segment.
     * @param data The PCM audio data.
     * @param offset The offset in the data array.
     * @param length The number of bytes to write.
     */
    void onSegmentData(byte[] data, int offset, int length);

    /**
     * Adds an audio tag to the current segment.
     * @param tag The tag to add.
     */
    void onTag(AudioTag tag);

    /**
     * Exports the last X seconds of audio to a single file.
     * @param durationSeconds The duration of the audio to export.
     * @param fileName The name of the exported file.
     * @return The exported file.
     */
    File export(float durationSeconds, String fileName) throws IOException;

    /**
     * Closes the store manager and releases any resources.
     */
    void close();
}
