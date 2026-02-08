package eu.mrogalski.saidit;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Encodes PCM 16-bit mono to AAC-LC and writes into an MP4 (.m4a) container.
 * Thread-safe for single-producer usage on an audio thread.
 */
public class AacMp4Writer implements Closeable {
    private static final String TAG = "AacMp4Writer";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC; // "audio/mp4a-latm"

    private final int sampleRate;
    private final int channelCount;
    private final int pcmBytesPerSample = 2; // 16-bit PCM

    private final MediaCodec encoder;
    private final MediaMuxer muxer;
    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    private boolean muxerStarted = false;
    private int trackIndex = -1;
    private long totalPcmBytesWritten = 0;
    private long ptsUs = 0; // Monotonic presentation time in microseconds for input samples

    public AacMp4Writer(int sampleRate, int channelCount, int bitRate, File outFile) throws IOException {
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;

        MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        encoder = MediaCodec.createEncoderByType(MIME_TYPE);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public void write(byte[] data, int offset, int length) throws IOException {
        int remaining = length;
        int off = offset;
        while (remaining > 0) {
            int inIndex = encoder.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                ByteBuffer inBuf = encoder.getInputBuffer(inIndex);
                if (inBuf == null) continue;
                inBuf.clear();
                int toCopy = Math.min(remaining, inBuf.remaining());
                inBuf.put(data, off, toCopy);
                long inputPts = ptsUs;
                int sampleCount = toCopy / pcmBytesPerSample / channelCount;
                ptsUs += (sampleCount * 1_000_000L) / sampleRate;
                encoder.queueInputBuffer(inIndex, 0, toCopy, inputPts, 0);
                off += toCopy;
                remaining -= toCopy;
                totalPcmBytesWritten += toCopy;
            } else {
                // No input buffer available, try draining and retry
                drainEncoder(false);
            }
        }
        drainEncoder(false);
    }

    private void drainEncoder(boolean endOfStream) throws IOException {
        if (endOfStream) {
            int inIndex = encoder.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                encoder.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                // If we couldn't queue EOS now, we'll retry on next drain.
            }
        }
        while (true) {
            int outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000);
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break;
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted) throw new IllegalStateException("Format changed twice");
                MediaFormat newFormat = encoder.getOutputFormat();
                trackIndex = muxer.addTrack(newFormat);
                muxer.start();
                muxerStarted = true;
            } else if (outIndex >= 0) {
                ByteBuffer outBuf = encoder.getOutputBuffer(outIndex);
                if (outBuf != null && bufferInfo.size > 0) {
                    outBuf.position(bufferInfo.offset);
                    outBuf.limit(bufferInfo.offset + bufferInfo.size);
                    if (!muxerStarted) {
                        // This should not happen, but guard anyway
                        Log.w(TAG, "Muxer not started when output available, dropping frame");
                    } else {
                        muxer.writeSampleData(trackIndex, outBuf, bufferInfo);
                    }
                }
                encoder.releaseOutputBuffer(outIndex, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            drainEncoder(true);
        } catch (Exception e) {
            Log.e(TAG, "Error finishing encoder", e);
        }
        try {
            encoder.stop();
        } catch (Exception ignored) {}
        try {
            encoder.release();
        } catch (Exception ignored) {}
        try {
            if (muxerStarted) {
                muxer.stop();
            }
        } catch (Exception ignored) {}
        try {
            muxer.release();
        } catch (Exception ignored) {}
    }

    public int getTotalSampleBytesWritten() {
        // Safe cast, typical sizes will fit; use long if you prefer.
        return (int)Math.min(Integer.MAX_VALUE, totalPcmBytesWritten);
    }
}
