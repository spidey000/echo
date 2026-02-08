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

public class AacExporter {
    private static final String TAG = "AacExporter";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;

    public static void export(File pcmFile, File aacFile, int sampleRate, int channels, int bitRate) throws IOException {
        MediaCodec encoder = null;
        MediaMuxer muxer = null;

        try {
            MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);

            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            muxer = new MediaMuxer(aacFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int trackIndex = -1;
            boolean muxerStarted = false;

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            ByteBuffer[] inputBuffers = encoder.getInputBuffers();
            ByteBuffer[] outputBuffers = encoder.getOutputBuffers();

            boolean inputDone = false;
            long presentationTimeUs = 0;

            try (FileInputStream fis = new FileInputStream(pcmFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;

                while (!inputDone) {
                    int inputBufferIndex = encoder.dequeueInputBuffer(10000);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                        inputBuffer.clear();
                        bytesRead = fis.read(buffer);
                        if (bytesRead == -1) {
                            encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            inputBuffer.put(buffer, 0, bytesRead);
                            presentationTimeUs = 1000000L * (pcmFile.length() - fis.available()) / (sampleRate * 2);
                            encoder.queueInputBuffer(inputBufferIndex, 0, bytesRead, presentationTimeUs, 0);
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
                muxer.stop();
                muxer.release();
            }
        }
    }
}
