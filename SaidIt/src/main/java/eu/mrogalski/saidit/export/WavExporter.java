package eu.mrogalski.saidit.export;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WavExporter implements AudioExporter {

    @Override
    public void export(File pcmFile, File outputFile, int sampleRate, int channels, int bitDepth) throws IOException {
        // bitDepth: 16, 24, 32 (float)
        
        try (FileInputStream fis = new FileInputStream(pcmFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            // Write WAV Header placeholder
            writeWavHeader(fos, 0, 0, sampleRate, channels, bitDepth);

            long totalAudioLen = 0;
            byte[] buffer = new byte[4096];
            int bytesRead;

            if (bitDepth == 16) {
                while ((bytesRead = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalAudioLen += bytesRead;
                }
            } else if (bitDepth == 24) {
                // Convert 16-bit to 24-bit
                while ((bytesRead = fis.read(buffer)) != -1) {
                    for (int i = 0; i < bytesRead; i += 2) {
                        int sample = (buffer[i] & 0xFF) | (buffer[i + 1] << 8); // 16-bit signed
                        // Convert to 24-bit: shift left by 8 bits
                        int sample24 = sample << 8;
                        fos.write(sample24 & 0xFF);
                        fos.write((sample24 >> 8) & 0xFF);
                        fos.write((sample24 >> 16) & 0xFF);
                        totalAudioLen += 3;
                    }
                }
            } else if (bitDepth == 32) {
                // Convert 16-bit to 32-bit float
                ByteBuffer floatBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                while ((bytesRead = fis.read(buffer)) != -1) {
                    for (int i = 0; i < bytesRead; i += 2) {
                        short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                        float floatSample = sample / 32768.0f;
                        floatBuffer.clear();
                        floatBuffer.putFloat(floatSample);
                        fos.write(floatBuffer.array());
                        totalAudioLen += 4;
                    }
                }
            }

            // Update WAV Header with correct size
            long totalDataLen = totalAudioLen + 36;
            writeWavHeader(fos, totalAudioLen, totalDataLen, sampleRate, channels, bitDepth);
        }
    }

    private void writeWavHeader(FileOutputStream out, long totalAudioLen, long totalDataLen, int longSampleRate, int channels, int bitDepth) throws IOException {
        byte[] header = new byte[44];
        
        long byteRate = longSampleRate * channels * (bitDepth / 8);

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = (byte) (bitDepth == 32 ? 3 : 1); // format = 1 (PCM), 3 (IEEE Float)
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * (bitDepth / 8)); // block align
        header[33] = 0;
        header[34] = (byte) bitDepth; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.getChannel().position(0);
        out.write(header, 0, 44);
    }
}
