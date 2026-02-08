package eu.mrogalski.saidit.vad;

import android.util.Log;

/**
 * A simple energy-based Voice Activity Detector.
 * This is a placeholder implementation and should be replaced with a more robust VAD.
 */
public class EnergyVad implements Vad {
    private static final String TAG = "EnergyVad";
    private static final double ENERGY_THRESHOLD = 32.0; // Corresponds to -30 dBFS for 16-bit PCM

    private int sampleRate;
    private int mode = 2; // Default sensitivity

    @Override
    public void init(int sampleRate) {
        this.sampleRate = sampleRate;
        Log.d(TAG, "Initialized with sample rate: " + sampleRate);
    }

    @Override
    public void setMode(int mode) {
        this.mode = mode;
        Log.d(TAG, "Mode set to: " + mode);
    }

    @Override
    public boolean process(byte[] pcm, int offset, int length) {
        if (length == 0) {
            return false;
        }

        double sum = 0.0;
        int count = length / 2;

        for (int i = 0; i < count; i++) {
            int index = offset + i * 2;
            // Assuming 16-bit little-endian PCM
            short sample = (short) ((pcm[index + 1] << 8) | (pcm[index] & 0xFF));
            sum += (sample / 32768.0) * (sample / 32768.0);
        }

        double rms = Math.sqrt(sum / count);
        double energy = 20 * Math.log10(rms);

        // This is a very simplistic threshold and doesn't account for noise floor.
        // A real implementation would use a more adaptive threshold.
        return energy > -getThresholdForMode(mode);
    }

    private double getThresholdForMode(int mode) {
        switch (mode) {
            case 0: return 25.0; // Least sensitive
            case 1: return 30.0;
            case 2: return 35.0;
            case 3: return 40.0; // Most sensitive
            default: return 35.0;
        }
    }

    @Override
    public void close() {
        Log.d(TAG, "Closing VAD.");
    }
}
