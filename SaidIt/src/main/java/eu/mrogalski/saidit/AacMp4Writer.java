package eu.mrogalski.saidit;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encodes PCM 16-bit mono to AAC-LC and writes into an MP4 (.m4a) container.
 * Thread-safe for single-producer usage on an audio thread.
 */
public class AacMp4Writer implements AutoCloseable {
    private static final String TAG = "AacMp4Writer";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC; // "audio/mp4a-latm"

    private final File outputFile;
    private MediaMuxer mediaMuxer;
    private MediaCodec mediaCodec;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final Object writeLock = new Object();
    private long totalBytesWritten = 0;
    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private int trackIndex = -1;
    private long presentationTimeUs = 0;
    private int sampleRate;

    public AacMp4Writer(int sampleRate, int channelCount, int bitRate, File outputFile) throws IOException {
        this.outputFile = outputFile;
        this.sampleRate = sampleRate;

        MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaMuxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mediaCodec.start();
    }

    public void write(byte[] data, int offset, int length) throws IOException {
        synchronized (writeLock) {
            if (isClosed.get()) {
                throw new IOException("Writer is closed");
            }
            if (length == 0) {
                return;
            }
            drainEncoder();
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(data, offset, length);
                presentationTimeUs += (long) (1000000L * (length / 2) / sampleRate);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, length, presentationTimeUs, 0);
                totalBytesWritten += length;
            }
        }
    }
    
    private void drainEncoder() {
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        while (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
            if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                if (trackIndex == -1) {
                    MediaFormat newFormat = mediaCodec.getOutputFormat();
                    trackIndex = mediaMuxer.addTrack(newFormat);
                    mediaMuxer.start();
                }
            } else if (trackIndex != -1 && bufferInfo.size != 0 && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                mediaMuxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
            }
            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    public long getTotalSampleBytesWritten() {
        return totalBytesWritten;
    }
    
    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            synchronized (writeLock) {
                closeQuietly(mediaCodec);
                closeQuietly(mediaMuxer);
                mediaCodec = null;
                mediaMuxer = null;
            }
        }
    }
    
    private void closeQuietly(Object closeable) {
        if (closeable != null) {
            try {
                if (closeable instanceof MediaCodec) {
                    MediaCodec codec = (MediaCodec) closeable;
                    codec.stop();
                    codec.release();
                } else if (closeable instanceof MediaMuxer) {
                    MediaMuxer muxer = (MediaMuxer) closeable;
                    try {
                        muxer.stop();
                    } catch (IllegalStateException e) {
                        // Muxer might not have been started
                    }
                    muxer.release();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing resource", e);
            }
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}
