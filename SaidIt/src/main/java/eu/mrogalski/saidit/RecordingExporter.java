package eu.mrogalski.saidit;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import eu.mrogalski.saidit.export.AacExporter;
import eu.mrogalski.saidit.storage.RecordingStoreManager;

public class RecordingExporter {
    private static final String TAG = "RecordingExporter";

    private final Context mContext;
    private final int mSampleRate;

    public RecordingExporter(Context context, int sampleRate) {
        mContext = context;
        mSampleRate = sampleRate;
    }

    public void export(RecordingStoreManager recordingStoreManager, float memorySeconds, String format, String newFileName, SaidItService.WavFileReceiver wavFileReceiver) {
        File exportFile = null;
        File aacFile = null;
        try {
            String fileName = newFileName != null ? newFileName.replaceAll("[^a-zA-Z0-9.-]", "_") : "SaidIt_export";
            exportFile = recordingStoreManager.export(memorySeconds, fileName);

            if (exportFile != null && wavFileReceiver != null) {
                if ("aac".equals(format)) {
                    aacFile = new File(mContext.getCacheDir(), fileName + ".m4a");
                    AacExporter.export(exportFile, aacFile, mSampleRate, 1, 96000);
                    saveFileToMediaStore(aacFile, (newFileName != null ? newFileName : "SaidIt Recording") + ".m4a", "audio/mp4", wavFileReceiver);
                } else {
                    saveFileToMediaStore(exportFile, (newFileName != null ? newFileName : "SaidIt Recording") + ".wav", "audio/wav", wavFileReceiver);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "ERROR exporting file", e);
            showToast(mContext.getString(R.string.error_saving_recording));
            if (wavFileReceiver != null) {
                wavFileReceiver.onFailure(e);
            }
        } finally {
            if (exportFile != null && !exportFile.delete()) {
                Log.w(TAG, "Could not delete export file: " + exportFile.getAbsolutePath());
            }
            if (aacFile != null && !aacFile.delete()) {
                Log.w(TAG, "Could not delete aac file: " + aacFile.getAbsolutePath());
            }
        }
    }

    public void saveFileToMediaStore(File sourceFile, String displayName, String mimeType, SaidItService.WavFileReceiver receiver) {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        Uri collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri itemUri = resolver.insert(collection, values);

        if (itemUri == null) {
            Log.e(TAG, "Error creating MediaStore entry.");
            if (receiver != null) {
                new Handler(Looper.getMainLooper()).post(() -> receiver.onFailure(new IOException("Failed to create MediaStore entry.")));
            }
            return;
        }

        try (InputStream in = Files.newInputStream(sourceFile.toPath());
             OutputStream out = resolver.openOutputStream(itemUri)) {
            if (out == null) {
                throw new IOException("Failed to open output stream for " + itemUri);
            }
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving file to MediaStore", e);
            resolver.delete(itemUri, null, null);
            itemUri = null;
        } finally {
            values.clear();
            values.put(MediaStore.Audio.Media.IS_PENDING, 0);
            if (itemUri != null) {
                resolver.update(itemUri, values, null, null);
                final Uri finalUri = itemUri;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (receiver != null) {
                        receiver.onSuccess(finalUri);
                    }
                });
            } else {
                if (receiver != null) {
                    new Handler(Looper.getMainLooper()).post(() -> receiver.onFailure(new IOException("Failed to write to MediaStore")));
                }
            }
            if (!sourceFile.delete()) {
                Log.w(TAG, "Could not delete source file: " + sourceFile.getAbsolutePath());
            }
        }
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(mContext, message, Toast.LENGTH_LONG).show());
    }
}
