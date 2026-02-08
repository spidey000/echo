package eu.mrogalski.saidit;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.MediaMetadataRetriever;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.net.Uri;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

 

import static eu.mrogalski.saidit.SaidIt.AUDIO_MEMORY_ENABLED_KEY;
import static eu.mrogalski.saidit.SaidIt.AUDIO_MEMORY_SIZE_KEY;
import static eu.mrogalski.saidit.SaidIt.PACKAGE_NAME;
import static eu.mrogalski.saidit.SaidIt.SAMPLE_RATE_KEY;

public class SaidItService extends Service {
    static final String TAG = SaidItService.class.getSimpleName();
    private static final int FOREGROUND_NOTIFICATION_ID = 458;
    private static final String YOUR_NOTIFICATION_CHANNEL_ID = "SaidItServiceChannel";
    private static final String ACTION_AUTO_SAVE = "eu.mrogalski.saidit.ACTION_AUTO_SAVE";

    volatile int SAMPLE_RATE;
    volatile int FILL_RATE;

    File mediaFile;
    AudioRecord audioRecord; // used only in the audio thread
    AacMp4Writer aacWriter; // used only in the audio thread
    final AudioMemory audioMemory = new AudioMemory(new SystemClockWrapper()); // used only in the audio thread

    HandlerThread audioThread;
    Handler audioHandler; // used to post messages to audio thread
    AudioMemory.Consumer filler;
    Runnable audioReader;
    AudioRecord.OnRecordPositionUpdateListener positionListener;

    int state;
    static final int STATE_READY = 0;
    static final int STATE_LISTENING = 1;
    static final int STATE_RECORDING = 2;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Reading native sample rate");

        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        SAMPLE_RATE = preferences.getInt(SAMPLE_RATE_KEY, AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC));
        Log.d(TAG, "Sample rate: " + SAMPLE_RATE);
        FILL_RATE = 2 * SAMPLE_RATE;

        audioThread = new HandlerThread("audioThread", Thread.MAX_PRIORITY);
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());

        filler = (array, offset, count) -> {
            if (audioRecord == null) return 0;
            final int read = audioRecord.read(array, offset, count, AudioRecord.READ_NON_BLOCKING);
            if (read < 0) {
                Log.e(TAG, "AUDIO RECORD ERROR: " + read);
                return 0;
            }
            if (aacWriter != null && read > 0) {
                aacWriter.write(array, offset, read);
            }
            return read;
        };

        audioReader = () -> {
            try {
                audioMemory.fill(filler);
            } catch (IOException e) {
                final String errorMessage = getString(R.string.error_during_recording_into) + (mediaFile != null ? mediaFile.getName() : "");
                showToast(errorMessage);
                Log.e(TAG, errorMessage, e);
                stopRecording(new SaidItFragment.NotifyFileReceiver(SaidItService.this));
            }
        };

        if (preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) {
            innerStartListening();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording(null);
        innerStopListening();
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new BackgroundRecorderBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_AUTO_SAVE.equals(intent.getAction())) {
            SharedPreferences preferences = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
            if (preferences.getBoolean("auto_save_enabled", false)) {
                Log.d(TAG, "Executing auto-save...");
                String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US).format(new java.util.Date());
                String autoName = "Auto-save_" + timestamp;
                dumpRecording(300, new SaidItFragment.NotifyFileReceiver(this), autoName);
            }
            return START_STICKY;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification());
        }
        return START_STICKY;
    }

    private void innerStartListening() {
        if (state != STATE_READY) return;
        state = STATE_LISTENING;

        Log.d(TAG, "Queueing: START LISTENING");
        startService(new Intent(this, this.getClass()));
        final long memorySize = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE).getLong(AUDIO_MEMORY_SIZE_KEY, Runtime.getRuntime().maxMemory() / 4);

        audioHandler.post(() -> {
            Log.d(TAG, "Executing: START LISTENING");
            @SuppressLint("MissingPermission")
            AudioRecord newAudioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    AudioMemory.CHUNK_SIZE);

            if (newAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Audio: INITIALIZATION ERROR");
                newAudioRecord.release();
                state = STATE_READY;
                return;
            }
            audioRecord = newAudioRecord;
            audioMemory.allocate(memorySize);
            // Set up event-driven periodic callbacks (~50ms)
            final int periodFrames = Math.max(128, SAMPLE_RATE / 20);
            positionListener = new AudioRecord.OnRecordPositionUpdateListener() {
                @Override
                public void onPeriodicNotification(AudioRecord recorder) {
                    audioHandler.post(audioReader);
                }
                @Override
                public void onMarkerReached(AudioRecord recorder) { }
            };
            audioRecord.setRecordPositionUpdateListener(positionListener, audioHandler);
            audioRecord.setPositionNotificationPeriod(periodFrames);
            audioRecord.startRecording();
            // Kickstart a first read to reduce latency
            audioHandler.post(audioReader);
        });

        scheduleAutoSave();
    }

    private void innerStopListening() {
        if (state == STATE_READY) return;
        state = STATE_READY;

        Log.d(TAG, "Queueing: STOP LISTENING");
        cancelAutoSave();
        stopForeground(true);
        stopService(new Intent(this, this.getClass()));

        audioHandler.post(() -> {
            Log.d(TAG, "Executing: STOP LISTENING");
            if (audioRecord != null) {
                try { audioRecord.setRecordPositionUpdateListener(null); } catch (Exception ignored) {}
                audioRecord.release();
                audioRecord = null;
            }
            audioHandler.removeCallbacks(audioReader);
            audioMemory.allocate(0);
        });
    }

    public void enableListening() {
        getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE)
                .edit().putBoolean(AUDIO_MEMORY_ENABLED_KEY, true).apply();
        innerStartListening();
    }

    public void disableListening() {
        getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE)
                .edit().putBoolean(AUDIO_MEMORY_ENABLED_KEY, false).apply();
        innerStopListening();
    }

    public void startRecording(final float prependedMemorySeconds) {
        if (state == STATE_RECORDING) return;
        if (state == STATE_READY) innerStartListening();
        state = STATE_RECORDING;

        audioHandler.post(() -> {
            flushAudioRecord();
            try {
                mediaFile = File.createTempFile("saidit", ".m4a", getCacheDir());
                // 96 kbps for mono voice
                aacWriter = new AacMp4Writer(SAMPLE_RATE, 1, 96_000, mediaFile);
                Log.d(TAG, "Recording to: " + mediaFile.getAbsolutePath());

                // Write prepended memory
                if (prependedMemorySeconds > 0) {
                    final int bytesPerSecond = (int) (1f / getBytesToSeconds());
                    final int bytesToDump = (int) (prependedMemorySeconds * bytesPerSecond);
                    audioMemory.dump((array, offset, count) -> { aacWriter.write(array, offset, count); return count; }, bytesToDump);
                }
            } catch (IOException e) {
                Log.e(TAG, "ERROR creating AAC/MP4 file", e);
                showToast(getString(R.string.error_creating_recording_file));
                state = STATE_LISTENING; // Revert state
            }
        });
    }

    public void stopRecording(final WavFileReceiver wavFileReceiver) {
        if (state != STATE_RECORDING) return;
        state = STATE_LISTENING;

        audioHandler.post(() -> {
            flushAudioRecord();
            if (aacWriter != null) {
                try {
                    aacWriter.close();
                } catch (IOException e) {
                    Log.e(TAG, "CLOSING ERROR", e);
                }
            }
            if (wavFileReceiver != null && mediaFile != null) {
                saveFileToMediaStore(mediaFile, mediaFile.getName(), wavFileReceiver);
            }
            aacWriter = null;
        });
    }

    public void dumpRecording(final float memorySeconds, final WavFileReceiver wavFileReceiver, String newFileName) {
        if (state == STATE_READY) return;

        audioHandler.post(() -> {
            flushAudioRecord();
            File dumpFile = null;
            try {
                String fileName = newFileName != null ? newFileName.replaceAll("[^a-zA-Z0-9.-]", "_") : "SaidIt_dump";
                dumpFile = new File(getCacheDir(), fileName + ".m4a");
                AacMp4Writer dumper = new AacMp4Writer(SAMPLE_RATE, 1, 96_000, dumpFile);
                Log.d(TAG, "Dumping to: " + dumpFile.getAbsolutePath());
                final int bytesPerSecond = (int) (1f / getBytesToSeconds());
                final int bytesToDump = (int) (memorySeconds * bytesPerSecond);
                audioMemory.dump((array, offset, count) -> { dumper.write(array, offset, count); return count; }, bytesToDump);
                dumper.close();

                if (wavFileReceiver != null) {
                    final File finalDumpFile = dumpFile;
                    saveFileToMediaStore(finalDumpFile, (newFileName != null ? newFileName : "SaidIt Recording") + ".m4a", wavFileReceiver);
                }
            } catch (IOException e) {
                Log.e(TAG, "ERROR dumping AAC/MP4 file", e);
                showToast(getString(R.string.error_saving_recording));
                if (dumpFile != null) {
                    dumpFile.delete();
                }
                if (wavFileReceiver != null) {
                    wavFileReceiver.onFailure(e);
                }
            }
        });
    }

    public void scheduleAutoSave() {
        SharedPreferences preferences = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        boolean autoSaveEnabled = preferences.getBoolean("auto_save_enabled", false);
        if (autoSaveEnabled) {
            long durationMillis = preferences.getInt("auto_save_duration", 600) * 1000L;
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, SaidItService.class);
            intent.setAction(ACTION_AUTO_SAVE);
            PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Log.d(TAG, "Scheduling auto-save for every " + durationMillis / 1000 + " seconds.");
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + durationMillis, durationMillis, pendingIntent);
        }
    }

    public void cancelAutoSave() {
        Log.d(TAG, "Cancelling auto-save.");
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, SaidItService.class);
        intent.setAction(ACTION_AUTO_SAVE);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(pendingIntent);
    }
    
    private void flushAudioRecord() {
        // Only allowed on the audio thread
        assert audioHandler.getLooper() == Looper.myLooper();
        audioHandler.removeCallbacks(audioReader); // remove any delayed callbacks
        audioReader.run();
    }
    
    private void showToast(String message) {
        Toast.makeText(SaidItService.this, message, Toast.LENGTH_LONG).show();
    }

    public long getMemorySize() {
        return audioMemory.getAllocatedMemorySize();
    }

    public void setMemorySize(final long memorySize) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putLong(AUDIO_MEMORY_SIZE_KEY, memorySize).apply();

        if(preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true)) {
            audioHandler.post(() -> {
                audioMemory.allocate(memorySize);
            });
        }
    }

    public int getSamplingRate() {
        return SAMPLE_RATE;
    }
    
    public void setSampleRate(int sampleRate) {
        if (state == STATE_RECORDING) return;
        if (state == STATE_READY) {
            final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
            preferences.edit().putInt(SAMPLE_RATE_KEY, sampleRate).apply();
            SAMPLE_RATE = sampleRate;
            FILL_RATE = 2 * SAMPLE_RATE;
            return;
        }

        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        preferences.edit().putInt(SAMPLE_RATE_KEY, sampleRate).apply();

        innerStopListening();
        SAMPLE_RATE = sampleRate;
        FILL_RATE = 2 * SAMPLE_RATE;
        innerStartListening();
    }

    public void getState(final StateCallback stateCallback) {
        final SharedPreferences preferences = this.getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);
        final boolean listeningEnabled = preferences.getBoolean(AUDIO_MEMORY_ENABLED_KEY, true);
        final boolean recording = (state == STATE_RECORDING);
        final Handler sourceHandler = new Handler(Looper.getMainLooper());
        audioHandler.post(() -> {
            flushAudioRecord();
            final AudioMemory.Stats stats = audioMemory.getStats(FILL_RATE);

            int recorded = 0;
            if(aacWriter != null) {
                recorded += aacWriter.getTotalSampleBytesWritten();
                recorded += stats.estimation;
            }
            final float bytesToSeconds = getBytesToSeconds();
            final int finalRecorded = recorded;
            sourceHandler.post(() -> stateCallback.state(listeningEnabled, recording,
                    (stats.overwriting ? stats.total : stats.filled + stats.estimation) * bytesToSeconds,
                    stats.total * bytesToSeconds,
                    finalRecorded * bytesToSeconds));
        });
    }

    public float getBytesToSeconds() {
        return 1f / FILL_RATE;
    }
    
    private void saveFileToMediaStore(File sourceFile, String displayName, WavFileReceiver receiver) {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        }

        Uri collection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Uri itemUri = resolver.insert(collection, values);

        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = resolver.openOutputStream(itemUri)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving file to MediaStore", e);
            if (itemUri != null) {
                resolver.delete(itemUri, null, null);
            }
            itemUri = null;
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Audio.Media.IS_PENDING, 0);
                if (itemUri != null) {
                    resolver.update(itemUri, values, null, null);
                }
            }

            if (itemUri != null) {
                final long duration = getMediaDuration(sourceFile);
                values.clear();
                values.put(MediaStore.Audio.Media.DURATION, duration);
                resolver.update(itemUri, values, null, null);

                final Uri finalUri = itemUri;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (receiver != null) {
                        receiver.onSuccess(finalUri, duration / 1000f);
                    }
                });
            }
            if (itemUri == null && receiver != null) {
                new Handler(Looper.getMainLooper()).post(() -> receiver.onFailure(new IOException("Failed to write to MediaStore")));
            }
            sourceFile.delete();
        }
    }

    private long getMediaDuration(File file) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());
            String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (dur != null) {
                return Long.parseLong(dur);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not read media duration", e);
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
        return 0;
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(YOUR_NOTIFICATION_CHANNEL_ID, "SaidIt Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        Intent notificationIntent = new Intent(this, SaidItActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, YOUR_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_hearing)
                .setContentIntent(pendingIntent)
                .build();
    }

    public interface WavFileReceiver {
        void onSuccess(Uri fileUri, float runtime);
        void onFailure(Exception e);
    }

    public interface StateCallback {
        void state(boolean listeningEnabled, boolean recording, float memorized, float totalMemory, float recorded);
    }

    class BackgroundRecorderBinder extends Binder {
        public SaidItService getService() {
            return SaidItService.this;
        }
    }
}
