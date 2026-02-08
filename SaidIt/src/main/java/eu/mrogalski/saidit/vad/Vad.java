package eu.mrogalski.saidit.vad;

public interface Vad {
    /**
     * Initializes the VAD with a specific sample rate.
     * @param sampleRate The sample rate of the audio to be processed.
     */
    void init(int sampleRate);

    /**
     * Sets the sensitivity mode of the VAD.
     * @param mode An integer from 0 (least sensitive) to 3 (most sensitive).
     */
    void setMode(int mode);

    /**
     * Processes a chunk of PCM audio to detect speech.
     * @param pcm A byte array containing 16-bit little-endian PCM audio.
     * @param offset The starting offset in the byte array.
     * @param length The number of bytes to process.
     * @return true if speech is detected, false otherwise.
     */
    boolean process(byte[] pcm, int offset, int length);

    /**
     * Closes the VAD and releases any resources.
     */
    void close();
}
