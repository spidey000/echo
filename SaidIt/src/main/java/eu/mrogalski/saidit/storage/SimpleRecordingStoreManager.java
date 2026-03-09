package eu.mrogalski.saidit.storage;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import eu.mrogalski.saidit.AudioMemory;
import eu.mrogalski.saidit.R;
import simplesound.pcm.WavAudioFormat;
import simplesound.pcm.WavFileWriter;

public class SimpleRecordingStoreManager implements RecordingStoreManager {
    private static final String TAG = "RecordingStoreManager";
    private static final String SEGMENTS_SUBDIR = "segments";
    private static final int MAX_SEGMENTS = 100; // Simple retention policy

    private final Context context;
    private final File storageDir;
    private final int sampleRate;
    private WavFileWriter currentWriter;
    private File currentFile;
    private File currentTagFile;
    private JSONArray currentTags;

    public SimpleRecordingStoreManager(Context context, int sampleRate) {
        this.context = context;
        this.sampleRate = sampleRate;
        File musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (musicDir == null) {
            // Fallback to internal storage if external is not available
            musicDir = new File(context.getFilesDir(), "Music");
        }
        this.storageDir = new File(musicDir, SEGMENTS_SUBDIR);
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            Log.e(TAG, "Failed to create storage directory.");
        }
    }

    @Override
    public void onSegmentStart(long timestamp) throws IOException {
        if (currentWriter != null) {
            Log.w(TAG, "Segment started without ending the previous one. Finalizing now.");
            onSegmentEnd(System.currentTimeMillis());
        }
        String fileName = new SimpleDateFormat("'segment'_yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date(timestamp));
        currentFile = new File(storageDir, fileName + ".tmp.wav");
        currentTagFile = new File(storageDir, fileName + ".tmp.json");
        currentTags = new JSONArray();
        currentWriter = new WavFileWriter(WavAudioFormat.wavFormat(sampleRate, 16, 1), currentFile);
        Log.d(TAG, "Started new segment file: " + currentFile.getAbsolutePath());
    }

    @Override
    public void onSegmentEnd(long timestamp) {
        if (currentWriter != null) {
            try {
                currentWriter.close();
                File finalFile = new File(currentFile.getAbsolutePath().replace(".tmp.wav", ".wav"));
                if (currentFile.renameTo(finalFile)) {
                    Log.d(TAG, "Segment finalized: " + finalFile.getAbsolutePath());
                } else {
                    Log.e(TAG, "Failed to rename segment file.");
                }

                // Save tags
                File finalTagFile = new File(currentTagFile.getAbsolutePath().replace(".tmp.json", ".json"));
                try (FileWriter fileWriter = new FileWriter(finalTagFile)) {
                    fileWriter.write(currentTags.toString());
                }
            } catch (IOException e) {
                Log.e(TAG, "Error finalizing segment file", e);
            }
        }
        currentWriter = null;
        currentFile = null;
        applyRetentionPolicy();
    }

    @Override
    public void onSegmentData(byte[] data, int offset, int length) {
        if (currentWriter != null) {
            try {
                currentWriter.write(data, offset, length);
            } catch (IOException e) {
                Log.e(TAG, "Error writing to segment file", e);
            }
        }
    }

    @Override
    public void onTag(AudioTag tag) {
        if (currentTags != null) {
            try {
                JSONObject tagJson = new JSONObject();
                tagJson.put("label", tag.getLabel());
                tagJson.put("confidence", tag.getConfidence());
                tagJson.put("timestamp", tag.getTimestamp());
                currentTags.put(tagJson);
            } catch (JSONException e) {
                Log.e(TAG, "Error creating tag JSON", e);
            }
        }
    }
    @Override
    public File export(float durationSeconds, String fileName) throws IOException {
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".wav"));
        if (files == null || files.length == 0) {
            return null;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

        File exportFile = new File(context.getCacheDir(), fileName + ".wav");
        WavFileWriter writer = new WavFileWriter(WavAudioFormat.wavFormat(sampleRate, 16, 1), exportFile);

        long bytesToExport = (long) (durationSeconds * sampleRate * 2);
        long bytesExported = 0;

        for (File file : files) {
            if (bytesExported >= bytesToExport) {
                break;
            }
            long fileBytes = file.length() - 44; // Exclude WAV header
            long bytesToWrite = Math.min(bytesToExport - bytesExported, fileBytes);

            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                fis.skip(44); // Skip WAV header
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) > 0 && bytesToWrite > 0) {
                    int toWrite = (int) Math.min(read, bytesToWrite);
                    writer.write(buffer, 0, toWrite);
                    bytesToWrite -= toWrite;
                    bytesExported += toWrite;
                }
            }
        }

        writer.close();
        return exportFile;
    }

    @Override
    public File exportFromBuffer(Object audioMemory, float durationSeconds, String fileName) throws IOException {
        Log.i(TAG, "exportFromBuffer: START - fileName=" + fileName + " durationSeconds=" + durationSeconds);
        
        if (!(audioMemory instanceof AudioMemory)) {
            Log.w(TAG, "exportFromBuffer: audioMemory is not an AudioMemory instance, type=" + (audioMemory != null ? audioMemory.getClass().getName() : "null"));
            return null;
        }

        AudioMemory memory = (AudioMemory) audioMemory;
        // Get stats with proper fill rate (sampleRate * 2 bytes per second)
        AudioMemory.Stats stats = memory.getStats(sampleRate * 2);

        Log.i(TAG, "exportFromBuffer: AudioMemory stats - filled=" + stats.filled + " bytes, total=" + stats.total + ", overwriting=" + stats.overwriting);
        
        if (stats.filled <= 0) {
            Log.w(TAG, "exportFromBuffer: AudioMemory is EMPTY (filled=" + stats.filled + "), cannot export");
            return null;
        }

        // Calculate how many bytes we actually want to export
        long requestedBytes = (long) (durationSeconds * sampleRate * 2);
        long bytesToExport = Math.min(requestedBytes, stats.filled);
        Log.i(TAG, "exportFromBuffer: requested=" + requestedBytes + " bytes, will export=" + bytesToExport + " bytes");

        File exportFile = new File(context.getCacheDir(), fileName + ".wav");
        final WavFileWriter[] writerRef = new WavFileWriter[1];
        AtomicLong bytesWritten = new AtomicLong(0);

        try {
            writerRef[0] = new WavFileWriter(WavAudioFormat.wavFormat(sampleRate, 16, 1), exportFile);
            Log.d(TAG, "exportFromBuffer: WAV file created at " + exportFile.getAbsolutePath());

            // Dump audio data from memory to file
            memory.dump((byte[] buffer, int offset, int count) -> {
                WavFileWriter writer = writerRef[0];
                if (writer == null) {
                    Log.e(TAG, "exportFromBuffer: writer is null in lambda!");
                    return 0;
                }
                
                if (bytesWritten.get() >= bytesToExport) {
                    return 0;
                }
                int toWrite = (int) Math.min(count, bytesToExport - bytesWritten.get());
                try {
                    writer.write(buffer, offset, toWrite);
                    bytesWritten.addAndGet(toWrite);
                    if (bytesWritten.get() % 8192 == 0 || bytesWritten.get() == bytesToExport) {
                        Log.d(TAG, "exportFromBuffer: progress - written=" + bytesWritten.get() + "/" + bytesToExport);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "exportFromBuffer: ERROR writing to export file", e);
                }
                return toWrite;
            }, (int) bytesToExport);
            
            Log.d(TAG, "exportFromBuffer: dump completed, total written=" + bytesWritten.get());
        } catch (Exception e) {
            Log.e(TAG, "exportFromBuffer: EXCEPTION during export", e);
            if (writerRef[0] != null) {
                try {
                    writerRef[0].close();
                } catch (IOException closeE) {
                    Log.e(TAG, "exportFromBuffer: error closing writer after exception", closeE);
                }
            }
            // Delete partial file
            if (exportFile.exists() && !exportFile.delete()) {
                Log.w(TAG, "exportFromBuffer: Could not delete partial export file: " + exportFile.getAbsolutePath());
            }
            throw e; // Re-throw to caller
        } finally {
            if (writerRef[0] != null) {
                try {
                    writerRef[0].close();
                    Log.d(TAG, "exportFromBuffer: writer closed");
                } catch (IOException e) {
                    Log.e(TAG, "exportFromBuffer: error closing writer", e);
                }
            }
        }

        if (bytesWritten.get() == 0) {
            Log.w(TAG, "exportFromBuffer: No bytes written, deleting empty file");
            if (exportFile.exists() && !exportFile.delete()) {
                Log.w(TAG, "exportFromBuffer: Could not delete empty export file: " + exportFile.getAbsolutePath());
            }
            return null;
        }

        Log.i(TAG, "exportFromBuffer: SUCCESS - exported " + bytesWritten.get() + " bytes to " + exportFile.getAbsolutePath());
        return exportFile;
    }

    private void applyRetentionPolicy() {
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".wav"));
        if (files != null && files.length > MAX_SEGMENTS) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - MAX_SEGMENTS; i++) {
                if (files[i].delete()) {
                    Log.d(TAG, "Deleted old segment: " + files[i].getName());
                } else {
                    Log.w(TAG, "Failed to delete old segment: " + files[i].getName());
                }
            }
        }
    }

    @Override
    public void close() {
        onSegmentEnd(System.currentTimeMillis());
    }
}
