package eu.mrogalski.saidit.analysis;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A simple implementation of a segmentation controller that uses speech/silence thresholds.
 */
public class SimpleSegmentationController implements SegmentationController {
    private static final String TAG = "SimpleSegmentation";

    // --- Configuration ---
    private final int sampleRate;
    private final int bytesPerMs;
    private long startThresholdMs = 200;
    private long endHangoverMs = 500;
    private long preRollMs = 300;
    private long maxSegmentMs = 30 * 60 * 1000; // 30 minutes

    // --- State ---
    private enum State { IDLE, IN_SPEECH, ENDING }
    private State state = State.IDLE;
    private long speechDurationMs = 0;
    private long silenceDurationMs = 0;
    private long currentSegmentDurationMs = 0;
    private SegmentListener listener;

    // --- Buffers ---
    private final Deque<byte[]> preRollBuffer = new ArrayDeque<>();
    private long preRollBufferBytes = 0;

    public SimpleSegmentationController(int sampleRate, int bitsPerSample) {
        this.sampleRate = sampleRate;
        this.bytesPerMs = (sampleRate * (bitsPerSample / 8)) / 1000;
    }

    @Override
    public void setListener(SegmentListener listener) {
        this.listener = listener;
    }

    @Override
    public void process(byte[] pcm, int offset, int length, boolean isSpeech) {
        long frameDurationMs = length / bytesPerMs;

        if (isSpeech) {
            handleSpeech(pcm, offset, length, frameDurationMs);
        } else {
            handleSilence(pcm, offset, length, frameDurationMs);
        }
    }

    private void handleSpeech(byte[] pcm, int offset, int length, long frameDurationMs) {
        silenceDurationMs = 0;
        speechDurationMs += frameDurationMs;
        currentSegmentDurationMs += frameDurationMs;

        if (state == State.IDLE && speechDurationMs >= startThresholdMs) {
            startSegment();
        }

        if (state == State.IN_SPEECH || state == State.ENDING) {
            if (listener != null) {
                listener.onSegmentData(pcm, offset, length);
            }
            if (currentSegmentDurationMs >= maxSegmentMs) {
                endSegment();
            }
        } else {
            // Buffer pre-roll while waiting for speech threshold
            bufferPreRoll(pcm, offset, length);
        }
        state = State.IN_SPEECH;
    }

    private void handleSilence(byte[] pcm, int offset, int length, long frameDurationMs) {
        speechDurationMs = 0;
        silenceDurationMs += frameDurationMs;

        if (state == State.IN_SPEECH && silenceDurationMs >= endHangoverMs) {
            state = State.ENDING;
        }

        if (state == State.ENDING) {
            endSegment();
        }

        if (state == State.IN_SPEECH || state == State.ENDING) {
            currentSegmentDurationMs += frameDurationMs;
            if (listener != null) {
                listener.onSegmentData(pcm, offset, length);
            }
        } else {
            bufferPreRoll(pcm, offset, length);
        }
    }

    private void startSegment() {
        Log.d(TAG, "Starting new segment.");
        state = State.IN_SPEECH;
        currentSegmentDurationMs = 0;
        if (listener != null) {
            listener.onSegmentStart(System.currentTimeMillis());
            // Drain pre-roll buffer
            for (byte[] data : preRollBuffer) {
                listener.onSegmentData(data, 0, data.length);
                currentSegmentDurationMs += data.length / bytesPerMs;
            }
        }
        preRollBuffer.clear();
        preRollBufferBytes = 0;
    }

    private void endSegment() {
        Log.d(TAG, "Ending segment.");
        if (state == State.IDLE) return;
        state = State.IDLE;
        if (listener != null) {
            listener.onSegmentEnd(System.currentTimeMillis());
        }
        reset();
    }

    private void bufferPreRoll(byte[] pcm, int offset, int length) {
        byte[] data = new byte[length];
        System.arraycopy(pcm, offset, data, 0, length);
        preRollBuffer.add(data);
        preRollBufferBytes += length;

        long preRollTargetBytes = preRollMs * bytesPerMs;
        while (preRollBufferBytes > preRollTargetBytes) {
            byte[] oldest = preRollBuffer.poll();
            if (oldest != null) {
                preRollBufferBytes -= oldest.length;
            }
        }
    }

    private void reset() {
        speechDurationMs = 0;
        silenceDurationMs = 0;
        currentSegmentDurationMs = 0;
        preRollBuffer.clear();
        preRollBufferBytes = 0;
    }

    @Override
    public void close() {
        endSegment();
    }
}
