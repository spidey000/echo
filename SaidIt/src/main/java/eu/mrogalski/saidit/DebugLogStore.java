package eu.mrogalski.saidit;

import static eu.mrogalski.saidit.SaidIt.DEBUG_LOGGING_ENABLED_KEY;
import static eu.mrogalski.saidit.SaidIt.DEFAULT_SAVE_RELATIVE_PATH;
import static eu.mrogalski.saidit.SaidIt.PACKAGE_NAME;
import static eu.mrogalski.saidit.SaidIt.SAVE_PATH_MODE_CUSTOM;
import static eu.mrogalski.saidit.SaidIt.SAVE_PATH_MODE_DEFAULT;
import static eu.mrogalski.saidit.SaidIt.SAVE_PATH_MODE_KEY;
import static eu.mrogalski.saidit.SaidIt.SAVE_RELATIVE_PATH_KEY;
import static eu.mrogalski.saidit.SaidIt.SAVE_TREE_URI_KEY;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Locale;

public final class DebugLogStore {
    private static final String TAG = "DebugLogStore";
    private static final String LOGS_DIR_NAME = "logs";
    private static final String LOG_FILE_NAME = "echo_debug.log";

    private DebugLogStore() {
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PACKAGE_NAME, Context.MODE_PRIVATE)
                .getBoolean(DEBUG_LOGGING_ENABLED_KEY, false);
    }

    public static synchronized void log(Context context, String tag, String message) {
        append(context, "I", tag, message, null);
    }

    public static synchronized void logError(Context context, String tag, String message, @Nullable Throwable error) {
        append(context, "E", tag, message, error);
    }

    @Nullable
    public static synchronized Uri getShareableLogUri(Context context) {
        try {
            SharedPreferences preferences = context.getSharedPreferences(PACKAGE_NAME, Context.MODE_PRIVATE);
            String mode = preferences.getString(SAVE_PATH_MODE_KEY, SAVE_PATH_MODE_DEFAULT);
            if (SAVE_PATH_MODE_CUSTOM.equals(mode)) {
                return getOrCreateCustomTreeLogUri(context, preferences);
            }
            return getOrCreateDefaultLogUri(context, preferences);
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve shareable log URI", e);
            return null;
        }
    }

    private static void append(Context context, String level, String tag, String message, @Nullable Throwable error) {
        if (!isEnabled(context)) {
            return;
        }

        String timestamp = DateFormat.format("yyyy-MM-dd HH:mm:ss", new Date()).toString();
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(Locale.US, "%s %s/%s: %s", timestamp, level, tag, message)).append('\n');
        if (error != null) {
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            builder.append(stack).append('\n');
        }

        byte[] data = builder.toString().getBytes();

        try {
            SharedPreferences preferences = context.getSharedPreferences(PACKAGE_NAME, Context.MODE_PRIVATE);
            String mode = preferences.getString(SAVE_PATH_MODE_KEY, SAVE_PATH_MODE_DEFAULT);
            if (SAVE_PATH_MODE_CUSTOM.equals(mode) && appendToCustomTree(context, preferences, data)) {
                return;
            }
            if (appendToDefaultLogs(context, preferences, data)) {
                return;
            }
            appendToAppExternalFallback(context, data);
        } catch (Exception e) {
            Log.w(TAG, "Failed to append debug log", e);
            appendToAppExternalFallback(context, data);
        }
    }

    private static boolean appendToCustomTree(Context context, SharedPreferences preferences, byte[] data) {
        Uri logUri = getOrCreateCustomTreeLogUri(context, preferences);
        if (logUri == null) {
            return false;
        }
        return appendToUri(context.getContentResolver(), logUri, data);
    }

    @Nullable
    private static Uri getOrCreateCustomTreeLogUri(Context context, SharedPreferences preferences) {
        String treeUriString = preferences.getString(SAVE_TREE_URI_KEY, null);
        if (treeUriString == null || treeUriString.isEmpty()) {
            return null;
        }
        Uri treeUri;
        try {
            treeUri = Uri.parse(treeUriString);
        } catch (Exception e) {
            return null;
        }

        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root == null || !root.canWrite()) {
            return null;
        }

        DocumentFile logsDir = root.findFile(LOGS_DIR_NAME);
        if (logsDir == null || !logsDir.isDirectory()) {
            logsDir = root.createDirectory(LOGS_DIR_NAME);
        }
        if (logsDir == null) {
            return null;
        }

        DocumentFile logFile = logsDir.findFile(LOG_FILE_NAME);
        if (logFile == null) {
            logFile = logsDir.createFile("text/plain", LOG_FILE_NAME);
        }
        return logFile != null ? logFile.getUri() : null;
    }

    private static boolean appendToDefaultLogs(Context context, SharedPreferences preferences, byte[] data) {
        Uri logUri = getOrCreateDefaultLogUri(context, preferences);
        if (logUri == null) {
            return false;
        }
        return appendToUri(context.getContentResolver(), logUri, data);
    }

    @Nullable
    private static Uri getOrCreateDefaultLogUri(Context context, SharedPreferences preferences) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }

        String relativePathBase = preferences.getString(SAVE_RELATIVE_PATH_KEY, DEFAULT_SAVE_RELATIVE_PATH);
        if (relativePathBase == null || relativePathBase.trim().isEmpty()) {
            relativePathBase = DEFAULT_SAVE_RELATIVE_PATH;
        }
        String relativePath = relativePathBase + "/" + LOGS_DIR_NAME + "/";

        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

        try (Cursor cursor = resolver.query(
                collection,
                new String[]{MediaStore.Files.FileColumns._ID},
                MediaStore.Files.FileColumns.DISPLAY_NAME + "=? AND " + MediaStore.Files.FileColumns.RELATIVE_PATH + "=?",
                new String[]{LOG_FILE_NAME, relativePath},
                MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return ContentUris.withAppendedId(collection, id);
            }
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, LOG_FILE_NAME);
        values.put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath);
        return resolver.insert(collection, values);
    }

    private static boolean appendToUri(ContentResolver resolver, Uri uri, byte[] data) {
        try (OutputStream out = resolver.openOutputStream(uri, "wa")) {
            if (out == null) {
                return false;
            }
            out.write(data);
            out.flush();
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed to append to URI: " + uri, e);
            return false;
        }
    }

    @Nullable
    public static File getFallbackLogFile(Context context) {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) {
            return null;
        }
        File dir = new File(base, LOGS_DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        return new File(dir, LOG_FILE_NAME);
    }

    private static void appendToAppExternalFallback(Context context, byte[] data) {
        File file = getFallbackLogFile(context);
        if (file == null) {
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            fos.write(data);
            fos.flush();
        } catch (IOException ignored) {
        }
    }
}
