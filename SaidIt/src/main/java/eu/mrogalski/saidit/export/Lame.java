package eu.mrogalski.saidit.export;

public class Lame {
    static {
        try {
            System.loadLibrary("mp3lame");
        } catch (UnsatisfiedLinkError e) {
            // Native library not found.
            // In a real implementation, ensure libmp3lame.so is present in jniLibs.
            // This class serves as a wrapper interface.
            System.err.println("LAME native library not loaded: " + e.getMessage());
        }
    }

    /**
     * Initialize LAME encoder.
     *
     * @param inSamplerate  input sample rate in Hz.
     * @param outChannel    number of output channels.
     * @param outSamplerate output sample rate in Hz.
     * @param outBitrate    brate control mode, bitrate in kbps.
     * @param quality       quality: 0=best, 9=worst. 2=near-best.
     */
    public native static void init(int inSamplerate, int outChannel, int outSamplerate, int outBitrate, int quality);

    /**
     * Encode buffer to mp3.
     *
     * @param buffer_l Left channel buffer.
     * @param buffer_r Right channel buffer.
     * @param samples  Number of samples per channel.
     * @param mp3buf   Result buffer.
     * @return Number of bytes output in mp3buf.
     */
    public native static int encode(short[] buffer_l, short[] buffer_r, int samples, byte[] mp3buf);

    /**
     * Flush LAME buffer.
     *
     * @param mp3buf Result buffer.
     * @return Number of bytes output in mp3buf.
     */
    public native static int flush(byte[] mp3buf);

    /**
     * Close LAME encoder.
     */
    public native static void close();
}
