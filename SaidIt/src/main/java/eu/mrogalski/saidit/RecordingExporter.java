package eu.mrogalski.saidit;

import static eu.mrogalski.saidit.SaidIt.PACKAGE_NAME;
import static eu.mrogalski.saidit.SaidIt.DEFAULT_SAVE_RELATIVE_PATH;
import static eu.mrogalski.saidit.SaidIt.SAVE_PATH_MODE_CUSTOM;
import static eu.mrogalski.saidit.SaidIt.SAVE_PATH_MODE_DEFAULT;
import static eu.mrogalski.saidit.SaidIt.SAVE_PATH_MODE_KEY;
import static eu.mrogalski.saidit.SaidIt.SAVE_RELATIVE_PATH_KEY;
import static eu.mrogalski.saidit.SaidIt.SAVE_TREE_URI_KEY;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import eu.mrogalski.saidit.export.AudioExporter;
import eu.mrogalski.saidit.export.Lame;
import eu.mrogalski.saidit.export.Mp3Exporter;
import eu.mrogalski.saidit.export.OpusExporter;
import eu.mrogalski.saidit.export.WavExporter;
import eu.mrogalski.saidit.storage.RecordingStoreManager;

public class RecordingExporter {
    private static final String TAG = "RecordingExporter";

    private final Context mContext;
    private final int mSampleRate;
    private final SharedPreferences mPreferences;

    public RecordingExporter(Context context, int sampleRate) {
        mContext = context;
        mSampleRate = sampleRate;
        mPreferences = context.getSharedPreferences(PACKAGE_NAME, Context.MODE_PRIVATE);
    }

    public void export(RecordingStoreManager recordingStoreManager,
                       float memorySeconds,
                       String format,
                       Integer bitrateOverride,
                       Integer bitDepthOverride,
                       String newFileName,
                       SaidItService.WavFileReceiver wavFileReceiver) {
        File exportFile = null;
        File pcmFile = null;
        File encodedFile = null;
        try {
            String selectedFormat = getSafeFormat(format);
            int bitRate = bitrateOverride != null ? bitrateOverride : mPreferences.getInt("export_bitrate", 32000);
            int bitDepth = bitDepthOverride != null ? bitDepthOverride : mPreferences.getInt("export_bit_depth", 16);

            String fileName = newFileName != null ? newFileName.replaceAll("[^a-zA-Z0-9.-]", "_") : "SaidIt_export";
            exportFile = recordingStoreManager.export(memorySeconds, fileName);

            if (exportFile == null) {
                throw new IOException("No audio available for export.");
            }

            String displayNameBase = newFileName != null ? newFileName : "SaidIt Recording";
            if ("wav".equals(selectedFormat) && bitDepth == 16) {
                saveFileToMediaStore(exportFile, displayNameBase + ".wav", "audio/wav", wavFileReceiver);
            } else {
                pcmFile = extractPcmFromWav(exportFile, fileName + "_pcm");
                encodedFile = buildEncodedFile(fileName, selectedFormat);
                AudioExporter exporter = createExporter(selectedFormat);
                exporter.export(pcmFile, encodedFile, mSampleRate, 1, "wav".equals(selectedFormat) ? bitDepth : bitRate);

                saveFileToMediaStore(
                        encodedFile,
                        displayNameBase + getExtension(selectedFormat),
                        getMimeType(selectedFormat),
                        wavFileReceiver
                );
            }
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Missing native dependency for export", e);
            showToast(mContext.getString(R.string.error_mp3_encoder_unavailable));
            if (wavFileReceiver != null) {
                wavFileReceiver.onFailure(new IOException("MP3 encoder library is unavailable.", e));
            }
        } catch (IOException e) {
            Log.e(TAG, "ERROR exporting file", e);
            showToast(mContext.getString(R.string.error_saving_recording));
            if (wavFileReceiver != null) {
                wavFileReceiver.onFailure(e);
            }
        } finally {
            if (exportFile != null && exportFile.exists() && !exportFile.delete()) {
                Log.w(TAG, "Could not delete export file: " + exportFile.getAbsolutePath());
            }
            if (pcmFile != null && pcmFile.exists() && !pcmFile.delete()) {
                Log.w(TAG, "Could not delete temp pcm file: " + pcmFile.getAbsolutePath());
            }
            if (encodedFile != null && encodedFile.exists() && !encodedFile.delete()) {
                Log.w(TAG, "Could not delete encoded file: " + encodedFile.getAbsolutePath());
            }
        }
    }

    private String getSafeFormat(String format) {
        if ("mp3".equals(format) || "opus".equals(format) || "wav".equals(format)) {
            return format;
        }
        String defaultFormat = mPreferences.getString("export_format", "wav");
        return defaultFormat != null ? defaultFormat : "wav";
    }

    private AudioExporter createExporter(String format) throws IOException {
        switch (format) {
            case "mp3":
                if (!Lame.isLibraryLoaded()) {
                    throw new IOException("MP3 encoder is unavailable on this build.");
                }
                return new Mp3Exporter();
            case "opus":
                return new OpusExporter();
            case "wav":
                return new WavExporter();
            default:
                throw new IOException("Unsupported export format: " + format);
        }
    }

    private File extractPcmFromWav(File wavFile, String baseName) throws IOException {
        File pcmFile = new File(mContext.getCacheDir(), baseName + ".pcm");
        try (FileInputStream inputStream = new FileInputStream(wavFile);
             FileOutputStream outputStream = new FileOutputStream(pcmFile)) {
            long skipped = inputStream.skip(44);
            if (skipped < 44) {
                throw new IOException("Invalid WAV header in exported source file.");
            }
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
        return pcmFile;
    }

    private File buildEncodedFile(String baseName, String format) {
        return new File(mContext.getCacheDir(), baseName + getExtension(format));
    }

    private String getExtension(String format) {
        switch (format) {
            case "mp3":
                return ".mp3";
            case "opus":
                return ".opus";
            case "wav":
            default:
                return ".wav";
        }
    }

    private String getMimeType(String format) {
        switch (format) {
            case "mp3":
                return "audio/mpeg";
            case "opus":
                return "audio/ogg";
            case "wav":
            default:
                return "audio/wav";
        }
    }

    public void saveFileToMediaStore(File sourceFile, String displayName, String mimeType, SaidItService.WavFileReceiver receiver) {
        Uri savedUri = null;
        boolean customAttempted = false;
        boolean usedDefaultFallback = false;

        try {
            String mode = mPreferences.getString(SAVE_PATH_MODE_KEY, SAVE_PATH_MODE_DEFAULT);
            if (SAVE_PATH_MODE_CUSTOM.equals(mode)) {
                customAttempted = true;
                savedUri = saveToCustomTree(sourceFile, displayName, mimeType);
                if (savedUri == null) {
                    Log.w(TAG, "custom_tree write failed: falling back to MediaStore default");
                }
            }

            if (savedUri == null) {
                if (customAttempted) {
                    usedDefaultFallback = true;
                }
                savedUri = saveToMediaStoreDefault(sourceFile, displayName, mimeType);
            }

            if (savedUri != null) {
                final Uri finalUri = savedUri;
                final boolean finalUsedDefaultFallback = usedDefaultFallback;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (receiver != null) {
                        receiver.onSuccess(finalUri);
                    }
                    if (finalUsedDefaultFallback) {
                        showToast(mContext.getString(R.string.save_location_fallback_used));
                    }
                });
            } else {
                throw new IOException("Failed to write recording to configured destination.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving recording", e);
            if (receiver != null) {
                new Handler(Looper.getMainLooper()).post(() -> receiver.onFailure(e));
            }
        } finally {
            if (!sourceFile.delete()) {
                Log.w(TAG, "Could not delete source file: " + sourceFile.getAbsolutePath());
            }
        }
    }

    private Uri saveToCustomTree(File sourceFile, String displayName, String mimeType) {
        String treeUriString = mPreferences.getString(SAVE_TREE_URI_KEY, null);
        if (treeUriString == null || treeUriString.isEmpty()) {
            Log.w(TAG, "custom_tree invalid_uri: missing URI");
            return null;
        }

        Uri treeUri;
        try {
            treeUri = Uri.parse(treeUriString);
        } catch (Exception e) {
            Log.w(TAG, "custom_tree invalid_uri: " + treeUriString, e);
            return null;
        }

        if (!hasPersistedWritePermission(treeUri)) {
            Log.w(TAG, "custom_tree permission_revoked: " + treeUri);
            return null;
        }

        try {
            Uri treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            Uri destinationUri = DocumentsContract.createDocument(mContext.getContentResolver(), treeDocumentUri, mimeType, displayName);
            if (destinationUri == null) {
                Log.w(TAG, "custom_tree write_failed: could not create document");
                return null;
            }
            copyFileToUri(sourceFile, destinationUri);
            return destinationUri;
        } catch (SecurityException e) {
            Log.w(TAG, "custom_tree permission_revoked while writing", e);
        } catch (IOException e) {
            Log.w(TAG, "custom_tree write_failed", e);
        }
        return null;
    }

    private Uri saveToMediaStoreDefault(File sourceFile, String displayName, String mimeType) throws IOException {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, getRelativeSavePath());
        }
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        Uri collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri itemUri = resolver.insert(collection, values);
        if (itemUri == null) {
            throw new IOException("Failed to create MediaStore entry.");
        }

        try {
            copyFileToUri(sourceFile, itemUri);
            values.clear();
            values.put(MediaStore.Audio.Media.IS_PENDING, 0);
            resolver.update(itemUri, values, null, null);
            return itemUri;
        } catch (IOException e) {
            resolver.delete(itemUri, null, null);
            throw e;
        }
    }

    private void copyFileToUri(File sourceFile, Uri destinationUri) throws IOException {
        try (InputStream in = Files.newInputStream(sourceFile.toPath());
             OutputStream out = mContext.getContentResolver().openOutputStream(destinationUri)) {
            if (out == null) {
                throw new IOException("Failed to open output stream for " + destinationUri);
            }
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }

    private boolean hasPersistedWritePermission(Uri treeUri) {
        for (android.content.UriPermission permission : mContext.getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(treeUri) && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    private String getRelativeSavePath() {
        String relative = mPreferences.getString(SAVE_RELATIVE_PATH_KEY, DEFAULT_SAVE_RELATIVE_PATH);
        if (relative == null || relative.trim().isEmpty()) {
            return DEFAULT_SAVE_RELATIVE_PATH;
        }
        String normalized = relative.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.isEmpty() ? DEFAULT_SAVE_RELATIVE_PATH : normalized;
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(mContext, message, Toast.LENGTH_LONG).show());
    }
}
