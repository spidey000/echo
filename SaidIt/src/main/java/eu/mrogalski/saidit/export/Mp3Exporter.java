package eu.mrogalski.saidit.export;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class Mp3Exporter implements AudioExporter {

    @Override
    public void export(File pcmFile, File outputFile, int sampleRate, int channels, int bitRate) throws IOException {
        int bufferSize = 7200 + (int) (1.25 * 7200); // Recommended buffer size for MP3 encoding
        byte[] mp3Buffer = new byte[bufferSize];
        short[] pcmBuffer = new short[bufferSize];
        
        // Convert bitrate to kbps
        int bitRateKbps = bitRate / 1000;
        if (bitRateKbps < 8) bitRateKbps = 8;
        if (bitRateKbps > 64) bitRateKbps = 64;

        try {
            Lame.init(sampleRate, channels, sampleRate, bitRateKbps, 2); // 2 = High quality

            try (FileInputStream fis = new FileInputStream(pcmFile);
                 FileOutputStream fos = new FileOutputStream(outputFile)) {
                
                // Assuming 16-bit PCM input
                byte[] byteBuffer = new byte[bufferSize * 2]; // 2 bytes per sample
                int bytesRead;

                while ((bytesRead = fis.read(byteBuffer)) > 0) {
                    int samplesRead = bytesRead / 2;
                    for (int i = 0; i < samplesRead; i++) {
                        // Little-endian conversion
                        int low = byteBuffer[i * 2] & 0xFF;
                        int high = byteBuffer[i * 2 + 1]; // Sign extension is correct here
                        pcmBuffer[i] = (short) ((high << 8) | low);
                    }

                    int encodedBytes = Lame.encode(pcmBuffer, pcmBuffer, samplesRead, mp3Buffer);
                    if (encodedBytes > 0) {
                        fos.write(mp3Buffer, 0, encodedBytes);
                    }
                }

                int flushBytes = Lame.flush(mp3Buffer);
                if (flushBytes > 0) {
                    fos.write(mp3Buffer, 0, flushBytes);
                }
            }
        } finally {
            Lame.close();
        }
    }
}
