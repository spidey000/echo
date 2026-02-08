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
