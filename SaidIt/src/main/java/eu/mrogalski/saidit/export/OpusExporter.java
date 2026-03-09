package eu.mrogalski.saidit.export;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class OpusExporter implements AudioExporter {
    private static final String TAG = "OpusExporter";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_OPUS;

    @Override
    public void export(File pcmFile, File outputFile, int sampleRate, int channels, int bitRate) throws IOException {
        MediaCodec encoder = null;
        MediaMuxer muxer = null;

        try {
            MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, channels);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            // Opus complexity (0-10), default 10 for highest quality
            format.setInteger(MediaFormat.KEY_COMPLEXITY, 10);

            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            // MUXER_OUTPUT_OGG is available since API 29
            muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG);
            int trackIndex = -1;
            boolean muxerStarted = false;

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            ByteBuffer[] inputBuffers = encoder.getInputBuffers();
            ByteBuffer[] outputBuffers = encoder.getOutputBuffers();

            boolean inputDone = false;
            long totalBytesQueued = 0;
            ByteBuffer pendingData = null; // holds leftover bytes from previous read
            int pendingDataLen = 0;

            try (FileInputStream fis = new FileInputStream(pcmFile)) {
                byte[] readBuffer = new byte[8192];

                while (!inputDone) {
                    int inputBufferIndex = encoder.dequeueInputBuffer(10000);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                        inputBuffer.clear();
                        int spaceAvailable = inputBuffer.remaining();
                        if (spaceAvailable == 0) {
                            continue;
                        }

                        int bytesAdded = 0;

                        // First, add any pending data from previous iteration
                        if (pendingData != null && pendingDataLen > 0) {
                            int toCopy = Math.min(pendingDataLen, spaceAvailable);
                            ByteBuffer src = pendingData.duplicate();
                            src.limit(toCopy);
                            inputBuffer.put(src);
                            pendingDataLen -= toCopy;
                            if (pendingDataLen == 0) {
                                pendingData = null;
                            } else {
                                // Still have leftover, adjust position for next use
                                pendingData.position(toCopy);
                            }
                            bytesAdded += toCopy;
                            spaceAvailable -= toCopy;
                        }

                        // Then, read more data from file if we still have space and not done
                        if (spaceAvailable > 0 && !inputDone) {
                            int bytesRead = fis.read(readBuffer);
                            if (bytesRead == -1) {
                                inputDone = true;
                            } else {
                                int toCopy = Math.min(bytesRead, spaceAvailable);
                                inputBuffer.put(readBuffer, 0, toCopy);
                                totalBytesQueued += toCopy;

                                // If we read more than we could fit, store remaining
                                if (toCopy < bytesRead) {
                                    pendingData = ByteBuffer.allocate(bytesRead - toCopy);
                                    pendingData.put(readBuffer, toCopy, bytesRead - toCopy);
                                    pendingData.flip();
                                    pendingDataLen = bytesRead - toCopy;
                                }
                            }
                        }

                        // If we added any data, queue the buffer
                        if (bytesAdded > 0 || (spaceAvailable > 0 && inputDone)) {
                            long presentationTimeUs = (totalBytesQueued * 1000000L) / (sampleRate * channels * 2);
                            int flags = inputDone ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
                            encoder.queueInputBuffer(inputBufferIndex, 0, bytesAdded, presentationTimeUs, flags);
                        }
                    }

                    int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000);
                    if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        trackIndex = muxer.addTrack(newFormat);
                        muxer.start();
                        muxerStarted = true;
                    } else if (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                        if (muxerStarted) {
                            // Adjust buffer info if necessary
                            // Opus in Ogg doesn't need ADTS header adjustment like AAC in ADTS (but MediaMuxer handles container)
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                        }
                        encoder.releaseOutputBuffer(outputBufferIndex, false);
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break;
                        }
                    }
                }
            }
        } finally {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (IllegalStateException e) {
                    // Muxer might not have started or already stopped
                }
                muxer.release();
            }
        }
    }
}
