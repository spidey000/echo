package eu.mrogalski.saidit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.material.textview.MaterialTextView;

import java.io.File;

import eu.mrogalski.android.TimeFormat;

public class SaidItFragment extends Fragment implements SaveClipBottomSheet.SaveClipListener {

    private static final String YOUR_NOTIFICATION_CHANNEL_ID = "SaidItServiceChannel";
    private BroadcastReceiver broadcastReceiver;

    // UI Elements
    private View recordingGroup;
    private View listeningGroup;
    private MaterialTextView recordingTime;
    private MaterialTextView historySize;
    private MaterialButtonToggleGroup listeningToggleGroup;

    // State
    private boolean isRecording = false;
    private float memorizedDuration = 0;


    private final MaterialButtonToggleGroup.OnButtonCheckedListener listeningToggleListener = (group, checkedId, isChecked) -> {
        if (isChecked) {
            Intent intent = new Intent(getActivity(), SaidItService.class);
            if (checkedId == R.id.listening_button) {
                intent.setAction(SaidItService.ACTION_START_LISTENING);
            } else if (checkedId == R.id.disabled_button) {
                intent.setAction(SaidItService.ACTION_STOP_LISTENING);
            }
            getActivity().startService(intent);
        }
    };

    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            if (getView() == null) return;
            Intent intent = new Intent(getActivity(), SaidItService.class);
            intent.setAction(SaidItService.ACTION_GET_STATE);
            getActivity().startService(intent);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_background_recorder, container, false);
        final Activity activity = requireActivity();

        // Find new UI elements
        Toolbar toolbar = rootView.findViewById(R.id.toolbar);
        recordingGroup = rootView.findViewById(R.id.recording_group);
        listeningGroup = rootView.findViewById(R.id.listening_group);
        recordingTime = rootView.findViewById(R.id.recording_time);
        historySize = rootView.findViewById(R.id.history_size);
        MaterialButton saveClipButton = rootView.findViewById(R.id.save_clip_button);
        MaterialButton settingsButton = rootView.findViewById(R.id.settings_button);
        MaterialButton recordingsButton = rootView.findViewById(R.id.recordings_button);
        MaterialButton stopRecordingButton = rootView.findViewById(R.id.rec_stop_button);
        listeningToggleGroup = rootView.findViewById(R.id.listening_toggle_group);
        // Set listeners
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_help) {
                startActivity(new Intent(requireActivity(), HowToActivity.class));
                return true;
            }
            return false;
        });
        settingsButton.setOnClickListener(v -> startActivity(new Intent(activity, SettingsActivity.class)));
        recordingsButton.setOnClickListener(v -> startActivity(new Intent(activity, RecordingsActivity.class)));

        stopRecordingButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SaidItService.class);
            intent.setAction(SaidItService.ACTION_STOP_RECORDING);
            getActivity().startService(intent);
        });

        saveClipButton.setOnClickListener(v -> {
            SaveClipBottomSheet bottomSheet = SaveClipBottomSheet.newInstance(memorizedDuration);
            bottomSheet.setSaveClipListener(this);
            bottomSheet.show(getParentFragmentManager(), "SaveClipBottomSheet");
        });

        listeningToggleGroup.addOnButtonCheckedListener(listeningToggleListener);

        return rootView;
    }

    @Override
    public void onSaveClip(String fileName, float durationInSeconds) {
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(requireActivity())
                .setTitle("Saving Recording")
                .setMessage("Please wait...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        Intent intent = new Intent(getActivity(), SaidItService.class);
        intent.setAction(SaidItService.ACTION_EXPORT_RECORDING);
        intent.putExtra(SaidItService.EXTRA_MEMORY_SECONDS, durationInSeconds);
        intent.putExtra(SaidItService.EXTRA_FORMAT, "aac");
        intent.putExtra(SaidItService.EXTRA_NEW_FILE_NAME, fileName);
        getActivity().startService(intent);
    }


    private final SaidItService.StateCallback serviceStateCallback = new SaidItService.StateCallback() {
        @Override
        public void state(final boolean listeningEnabled, final boolean recording, final float memorized, final float totalMemory, final float recorded) {
            memorizedDuration = memorized;
            
            if (isRecording != recording) {
                isRecording = recording;
                recordingGroup.setVisibility(recording ? View.VISIBLE : View.GONE);
                listeningGroup.setVisibility(recording ? View.GONE : View.VISIBLE);
            }

            if (isRecording) {
                recordingTime.setText(TimeFormat.shortTimer(recorded));
            } else {
                historySize.setText(TimeFormat.shortTimer(memorized));
            }

        // Update listening toggle state without triggering listener
            listeningToggleGroup.removeOnButtonCheckedListener(listeningToggleListener);
            if (listeningEnabled) {
                listeningToggleGroup.check(R.id.listening_button);
                listeningGroup.setAlpha(1.0f);
            } else {
                listeningToggleGroup.check(R.id.disabled_button);
                listeningGroup.setAlpha(0.5f);
            }
            listeningToggleGroup.addOnButtonCheckedListener(listeningToggleListener);

            if (getView() != null) {
                getView().postOnAnimationDelayed(updater, 100);
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (SaidItService.ACTION_STATE_UPDATE.equals(intent.getAction())) {
                    boolean listeningEnabled = intent.getBooleanExtra(SaidItService.EXTRA_LISTENING_ENABLED, false);
                    boolean recording = intent.getBooleanExtra(SaidItService.EXTRA_RECORDING, false);
                    float memorized = intent.getFloatExtra(SaidItService.EXTRA_MEMORIZED, 0);
                    float totalMemory = intent.getFloatExtra(SaidItService.EXTRA_TOTAL_MEMORY, 0);
                    float recorded = intent.getFloatExtra(SaidItService.EXTRA_RECORDED, 0);
                    serviceStateCallback.state(listeningEnabled, recording, memorized, totalMemory, recorded);
                }
            }
        };
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(broadcastReceiver, new IntentFilter(SaidItService.ACTION_STATE_UPDATE));
        if (getView() != null) {
            getView().postOnAnimation(updater);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(broadcastReceiver);
        if (getView() != null) {
            getView().removeCallbacks(updater);
        }
    }


    public void startTour() {
        // A small delay to ensure the UI is fully drawn before starting the tour.
        if (getView() != null) {
            getView().postDelayed(this::startInteractiveTour, 500);
        }
    }

    private void startInteractiveTour() {
        if (getActivity() == null || getView() == null) return;

        final TapTargetSequence sequence = new TapTargetSequence(getActivity())
                .targets(
                        TapTarget.forView(getView().findViewById(R.id.listening_toggle_group), getString(R.string.tour_listening_toggle_title), getString(R.string.tour_listening_toggle_desc))
                                .cancelable(false).tintTarget(false),
                        TapTarget.forView(getView().findViewById(R.id.history_size), getString(R.string.tour_memory_holds_title), getString(R.string.tour_memory_holds_desc))
                                .cancelable(false).tintTarget(false),
                        TapTarget.forView(getView().findViewById(R.id.save_clip_button), getString(R.string.tour_save_clip_title), getString(R.string.tour_save_clip_desc))
                                .cancelable(false).tintTarget(false),
                        TapTarget.forView(getView().findViewById(R.id.bottom_buttons_layout), getString(R.string.tour_bottom_buttons_title), getString(R.string.tour_bottom_buttons_desc))
                                .cancelable(false).tintTarget(false)
                );
        sequence.start();
    }

    // --- File Receiver and Notification Logic ---

    static Notification buildNotificationForFile(Context context, Uri fileUri, String fileName) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, "audio/mp4");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, YOUR_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.recording_saved))
                .setContentText(fileName)
                .setSmallIcon(R.drawable.ic_stat_notify_recorded)
                .setTicker(fileName)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        notificationBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        return notificationBuilder.build();
    }

    static class NotifyFileReceiver implements SaidItService.WavFileReceiver {
        private final Context context;

        public NotifyFileReceiver(Context context) {
            this.context = context;
        }

        @Override
        public void onSuccess(final Uri fileUri) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            notificationManager.notify(43, buildNotificationForFile(context, fileUri, "Recording Saved"));
        }

        @Override
        public void onFailure(Exception e) {
            // Do nothing for background notifications
        }
    }


    static class PromptFileReceiver implements SaidItService.WavFileReceiver {
        private final Activity activity;
        private final AlertDialog progressDialog;

        public PromptFileReceiver(Activity activity, AlertDialog dialog) {
            this.activity = activity;
            this.progressDialog = dialog;
        }

        public PromptFileReceiver(Activity activity) {
            this(activity, null);
        }

        @Override
        public void onSuccess(final Uri fileUri) {
            if (activity != null && !activity.isFinishing()) {
                activity.runOnUiThread(() -> {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.recording_done_title)
                            .setMessage("Recording saved to your music folder.")
                            .setPositiveButton(R.string.open, (dialog, which) -> {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setDataAndType(fileUri, "audio/mp4");
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                activity.startActivity(intent);
                            })
                            .setNeutralButton(R.string.share, (dialog, which) -> {
                                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                shareIntent.setType("audio/mp4");
                                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                activity.startActivity(Intent.createChooser(shareIntent, "Send to"));
                            })
                            .setNegativeButton(R.string.dismiss, null)
                            .show();
                });
            }
        }

        @Override
        public void onFailure(Exception e) {
            if (activity != null && !activity.isFinishing()) {
                activity.runOnUiThread(() -> {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.error_title)
                            .setMessage(R.string.error_saving_failed)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        }
    }
}
