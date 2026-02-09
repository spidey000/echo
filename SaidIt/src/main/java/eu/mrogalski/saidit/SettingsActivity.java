package eu.mrogalski.saidit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import static eu.mrogalski.saidit.SaidIt.PACKAGE_NAME;
import static eu.mrogalski.saidit.SaidIt.DEFAULT_SAVE_RELATIVE_PATH;
import static eu.mrogalski.saidit.SaidIt.SAVE_PATH_MODE_CUSTOM;
import static eu.mrogalski.saidit.SaidIt.SAVE_PATH_MODE_DEFAULT;
import static eu.mrogalski.saidit.SaidIt.SAVE_PATH_MODE_KEY;
import static eu.mrogalski.saidit.SaidIt.SAVE_RELATIVE_PATH_KEY;
import static eu.mrogalski.saidit.SaidIt.SAVE_TREE_URI_KEY;
import static eu.mrogalski.saidit.SaidIt.AUDIO_MEMORY_PRESETS;
import static eu.mrogalski.saidit.SaidIt.AUTO_SAVE_MAX_FILES_DEFAULT;
import static eu.mrogalski.saidit.SaidIt.AUTO_SAVE_MAX_FILES_KEY;

import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import eu.mrogalski.StringFormat;
import eu.mrogalski.android.TimeFormat;

public class SettingsActivity extends AppCompatActivity {

    private SaidItService service;
    private TextView historyLimitTextView;
    private MaterialButtonToggleGroup memoryToggleGroup;
    private MaterialButtonToggleGroup qualityToggleGroup;
    private Button memory32Button, memory64Button, memory128Button, memory256Button;
    private Button quality8kHzButton, quality16kHzButton, quality48kHzButton;
    private SwitchMaterial autoSaveSwitch;
    private Slider autoSaveMaxFilesSlider;
    private TextView autoSaveMaxFilesValue;
    private TextView autoSaveEstimatedDuration;

    private MaterialButtonToggleGroup exportFormatToggleGroup;
    private LinearLayout bitrateContainer;
    private Slider bitrateSlider;
    private TextView bitrateValue;
    private LinearLayout bitDepthContainer;
    private MaterialButtonToggleGroup bitDepthToggleGroup;
    private TextView estimatedSizeText;
    private MaterialButtonToggleGroup saveLocationToggleGroup;
    private Button saveLocationDefaultButton;
    private Button saveLocationCustomButton;
    private Button pickFolderButton;
    private Button resetSaveLocationButton;
    private TextView saveLocationDefaultPathValue;
    private TextView saveLocationCustomPathValue;
    private TextView saveLocationValue;
    private TextView saveLocationStatus;
    private TextView summaryBuffer;
    private TextView summaryQuality;
    private TextView summarySize;
    private TextView summaryAutosave;
    private TextView summaryStability;

    private SharedPreferences sharedPreferences;

    private boolean isBound = false;

    private final ActivityResultLauncher<Uri> folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri == null) {
                    syncSaveLocationUI();
                    return;
                }
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                    sharedPreferences.edit()
                            .putString(SAVE_TREE_URI_KEY, uri.toString())
                            .putString(SAVE_PATH_MODE_KEY, SAVE_PATH_MODE_CUSTOM)
                            .apply();
                } catch (SecurityException e) {
                    sharedPreferences.edit().putString(SAVE_PATH_MODE_KEY, SAVE_PATH_MODE_DEFAULT).apply();
                }
                syncSaveLocationUI();
            }
    );

    private final MaterialButtonToggleGroup.OnButtonCheckedListener memoryToggleListener = (group, checkedId, isChecked) -> {
        if (isChecked && isBound) {
            long memorySize = AUDIO_MEMORY_PRESETS[0];
            if (checkedId == R.id.memory_64) {
                memorySize = AUDIO_MEMORY_PRESETS[1];
            } else if (checkedId == R.id.memory_128) {
                memorySize = AUDIO_MEMORY_PRESETS[2];
            } else if (checkedId == R.id.memory_256) {
                memorySize = AUDIO_MEMORY_PRESETS[3];
            }
            service.setMemorySize(memorySize);
            updateHistoryLimit();
            updateAutoSaveEstimatedDuration();
        }
    };

    private final MaterialButtonToggleGroup.OnButtonCheckedListener qualityToggleListener = (group, checkedId, isChecked) -> {
        if (isChecked && isBound) {
            int sampleRate = 8000; // Default to 8kHz
            if (checkedId == R.id.quality_16kHz) {
                sampleRate = 16000;
            } else if (checkedId == R.id.quality_48kHz) {
                sampleRate = 48000;
            }
            service.setSampleRate(sampleRate);
            updateHistoryLimit();
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            SaidItService.BackgroundRecorderBinder typedBinder = (SaidItService.BackgroundRecorderBinder) binder;
            service = typedBinder.getService();
            isBound = true;
            syncUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize UI components
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        historyLimitTextView = findViewById(R.id.history_limit);
        memoryToggleGroup = findViewById(R.id.memory_toggle_group);
        qualityToggleGroup = findViewById(R.id.quality_toggle_group);
        memory32Button = findViewById(R.id.memory_32);
        memory64Button = findViewById(R.id.memory_64);
        memory128Button = findViewById(R.id.memory_128);
        memory256Button = findViewById(R.id.memory_256);
        quality8kHzButton = findViewById(R.id.quality_8kHz);
        quality16kHzButton = findViewById(R.id.quality_16kHz);
        quality48kHzButton = findViewById(R.id.quality_48kHz);
        autoSaveSwitch = findViewById(R.id.auto_save_switch);
        autoSaveMaxFilesSlider = findViewById(R.id.auto_save_max_files_slider);
        autoSaveMaxFilesValue = findViewById(R.id.auto_save_max_files_value);
        autoSaveEstimatedDuration = findViewById(R.id.auto_save_estimated_duration);

        exportFormatToggleGroup = findViewById(R.id.export_format_toggle_group);
        bitrateContainer = findViewById(R.id.bitrate_container);
        bitrateSlider = findViewById(R.id.bitrate_slider);
        bitrateValue = findViewById(R.id.bitrate_value);
        bitDepthContainer = findViewById(R.id.bit_depth_container);
        bitDepthToggleGroup = findViewById(R.id.bit_depth_toggle_group);
        estimatedSizeText = findViewById(R.id.estimated_size_text);
        saveLocationToggleGroup = findViewById(R.id.save_location_toggle_group);
        saveLocationDefaultButton = findViewById(R.id.save_location_default);
        saveLocationCustomButton = findViewById(R.id.save_location_custom);
        pickFolderButton = findViewById(R.id.pick_folder_button);
        resetSaveLocationButton = findViewById(R.id.reset_save_location_button);
        saveLocationDefaultPathValue = findViewById(R.id.save_location_default_path_value);
        saveLocationCustomPathValue = findViewById(R.id.save_location_custom_path_value);
        saveLocationValue = findViewById(R.id.save_location_value);
        saveLocationStatus = findViewById(R.id.save_location_status);
        summaryBuffer = findViewById(R.id.summary_buffer);
        summaryQuality = findViewById(R.id.summary_quality);
        summarySize = findViewById(R.id.summary_size);
        summaryAutosave = findViewById(R.id.summary_autosave);
        summaryStability = findViewById(R.id.summary_stability);

        Button howToButton = findViewById(R.id.how_to_button);
        Button showTourButton = findViewById(R.id.show_tour_button);

        sharedPreferences = getSharedPreferences(PACKAGE_NAME, MODE_PRIVATE);


        // Setup Toolbar
        toolbar.setNavigationOnClickListener(v -> finish());

        // Setup How-To Button
        howToButton.setOnClickListener(v -> startActivity(new Intent(this, HowToActivity.class)));
        showTourButton.setOnClickListener(v -> {
            sharedPreferences.edit().putBoolean("show_tour_on_next_launch", true).apply();
            finish();
        });

        // Setup Listeners
        memoryToggleGroup.addOnButtonCheckedListener(memoryToggleListener);
        qualityToggleGroup.addOnButtonCheckedListener(qualityToggleListener);

        autoSaveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("auto_save_enabled", isChecked).apply();
            autoSaveMaxFilesSlider.setEnabled(isChecked);
            autoSaveMaxFilesValue.setEnabled(isChecked);
            autoSaveEstimatedDuration.setEnabled(isChecked);
            updateAutoSaveEstimatedDuration();
            updateSummaryPanel();
        });

        autoSaveMaxFilesSlider.addOnChangeListener((slider, value, fromUser) -> {
            int maxFiles = (int) value;
            updateAutoSaveMaxFilesValue(maxFiles);
            if (fromUser) {
                sharedPreferences.edit().putInt(AUTO_SAVE_MAX_FILES_KEY, maxFiles).apply();
            }
            updateAutoSaveEstimatedDuration();
            updateSummaryPanel();
        });

        exportFormatToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                String format = "wav";
                if (checkedId == R.id.format_mp3) format = "mp3";
                else if (checkedId == R.id.format_opus) format = "opus";

                sharedPreferences.edit().putString("export_format", format).apply();
                updateExportSettingsUI(format);
                updateSummaryPanel();
            }
        });

        bitrateSlider.addOnChangeListener((slider, value, fromUser) -> {
            int bitrate = (int) value;
            bitrateValue.setText((bitrate / 1000) + " kbps");
            if (fromUser) {
                sharedPreferences.edit().putInt("export_bitrate", bitrate).apply();
            }
            updateEstimatedSize();
            updateSummaryPanel();
        });

        bitDepthToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                int depth = 16;
                if (checkedId == R.id.depth_24) depth = 24;
                else if (checkedId == R.id.depth_32) depth = 32;

                sharedPreferences.edit().putInt("export_bit_depth", depth).apply();
            }
            updateEstimatedSize();
            updateSummaryPanel();
        });

        saveLocationToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.save_location_default) {
                sharedPreferences.edit().putString(SAVE_PATH_MODE_KEY, SAVE_PATH_MODE_DEFAULT).apply();
            } else if (checkedId == R.id.save_location_custom) {
                sharedPreferences.edit().putString(SAVE_PATH_MODE_KEY, SAVE_PATH_MODE_CUSTOM).apply();
                String treeUri = sharedPreferences.getString(SAVE_TREE_URI_KEY, null);
                if (treeUri == null || treeUri.isEmpty()) {
                    folderPickerLauncher.launch(null);
                }
            }
            syncSaveLocationUI();
        });

        pickFolderButton.setOnClickListener(v -> folderPickerLauncher.launch(null));

        resetSaveLocationButton.setOnClickListener(v -> {
            String existingTree = sharedPreferences.getString(SAVE_TREE_URI_KEY, null);
            if (existingTree != null) {
                try {
                    Uri treeUri = Uri.parse(existingTree);
                    getContentResolver().releasePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                } catch (Exception ignored) {
                }
            }
            sharedPreferences.edit()
                    .putString(SAVE_PATH_MODE_KEY, SAVE_PATH_MODE_DEFAULT)
                    .putString(SAVE_RELATIVE_PATH_KEY, DEFAULT_SAVE_RELATIVE_PATH)
                    .remove(SAVE_TREE_URI_KEY)
                    .apply();
            syncSaveLocationUI();
        });

        if (!sharedPreferences.contains(SAVE_PATH_MODE_KEY)) {
            sharedPreferences.edit().putString(SAVE_PATH_MODE_KEY, SAVE_PATH_MODE_DEFAULT).apply();
        }
        if (!sharedPreferences.contains(SAVE_RELATIVE_PATH_KEY)) {
            sharedPreferences.edit().putString(SAVE_RELATIVE_PATH_KEY, DEFAULT_SAVE_RELATIVE_PATH).apply();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, SaidItService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    private void syncUI() {
        if (!isBound || service == null) return;

        // Remove listeners to prevent programmatic changes from triggering them
        memoryToggleGroup.removeOnButtonCheckedListener(memoryToggleListener);
        qualityToggleGroup.removeOnButtonCheckedListener(qualityToggleListener);

        memory32Button.setText(getString(R.string.memory_preset_32));
        memory64Button.setText(getString(R.string.memory_preset_64));
        memory128Button.setText(getString(R.string.memory_preset_128));
        memory256Button.setText(getString(R.string.memory_preset_256));

        long currentMemory = service.getMemorySize();
        long normalizedMemory = SaidIt.nearestAudioMemoryPreset(currentMemory);
        if (currentMemory != normalizedMemory) {
            service.setMemorySize(normalizedMemory);
            currentMemory = normalizedMemory;
        }
        if (currentMemory == AUDIO_MEMORY_PRESETS[0]) {
            memoryToggleGroup.check(R.id.memory_32);
        } else if (currentMemory == AUDIO_MEMORY_PRESETS[1]) {
            memoryToggleGroup.check(R.id.memory_64);
        } else if (currentMemory == AUDIO_MEMORY_PRESETS[2]) {
            memoryToggleGroup.check(R.id.memory_128);
        } else {
            memoryToggleGroup.check(R.id.memory_256);
        }

        // Set quality button state
        int currentRate = service.getSamplingRate();
        if (currentRate >= 48000) {
            qualityToggleGroup.check(R.id.quality_48kHz);
        } else if (currentRate >= 16000) {
            qualityToggleGroup.check(R.id.quality_16kHz);
        } else {
            qualityToggleGroup.check(R.id.quality_8kHz);
        }

        // Load and apply auto-save settings
        boolean autoSaveEnabled = sharedPreferences.getBoolean("auto_save_enabled", false);
        autoSaveSwitch.setChecked(autoSaveEnabled);
        int maxFiles = getAutoSaveMaxFiles();
        autoSaveMaxFilesSlider.setEnabled(autoSaveEnabled);
        autoSaveMaxFilesValue.setEnabled(autoSaveEnabled);
        autoSaveEstimatedDuration.setEnabled(autoSaveEnabled);
        autoSaveMaxFilesSlider.setValue(maxFiles);
        updateAutoSaveMaxFilesValue(maxFiles);
        updateAutoSaveEstimatedDuration();

        updateHistoryLimit();

        // Load export settings
        String format = sharedPreferences.getString("export_format", "wav");
        if ("mp3".equals(format)) exportFormatToggleGroup.check(R.id.format_mp3);
        else if ("opus".equals(format)) exportFormatToggleGroup.check(R.id.format_opus);
        else exportFormatToggleGroup.check(R.id.format_wav);

        int bitrate = sharedPreferences.getInt("export_bitrate", 32000);
        bitrate = Math.max(8000, Math.min(64000, bitrate));
        sharedPreferences.edit().putInt("export_bitrate", bitrate).apply();
        bitrateSlider.setValue(bitrate);
        bitrateValue.setText((bitrate / 1000) + " kbps");

        int bitDepth = sharedPreferences.getInt("export_bit_depth", 16);
        if (bitDepth == 24) bitDepthToggleGroup.check(R.id.depth_24);
        else if (bitDepth == 32) bitDepthToggleGroup.check(R.id.depth_32);
        else bitDepthToggleGroup.check(R.id.depth_16);

        updateExportSettingsUI(format);
        syncSaveLocationUI();
        updateSummaryPanel();

        // Re-add listeners
        memoryToggleGroup.addOnButtonCheckedListener(memoryToggleListener);
        qualityToggleGroup.addOnButtonCheckedListener(qualityToggleListener);
        
    }

    private void syncSaveLocationUI() {
        String mode = sharedPreferences.getString(SAVE_PATH_MODE_KEY, SAVE_PATH_MODE_DEFAULT);
        String relativePath = sharedPreferences.getString(SAVE_RELATIVE_PATH_KEY, DEFAULT_SAVE_RELATIVE_PATH);
        String treeUri = sharedPreferences.getString(SAVE_TREE_URI_KEY, null);
        String customFolderLabel = (treeUri == null || treeUri.isEmpty())
                ? getString(R.string.save_location_not_selected)
                : getCustomFolderLabel(treeUri);

        saveLocationDefaultPathValue.setText(getString(R.string.save_location_default_path_format, relativePath));
        saveLocationCustomPathValue.setText(getString(R.string.save_location_custom_path_format, customFolderLabel));

        if (SAVE_PATH_MODE_CUSTOM.equals(mode)) {
            saveLocationToggleGroup.check(R.id.save_location_custom);
            pickFolderButton.setEnabled(true);
            boolean hasPermission = hasWritePermission(treeUri);
            if (treeUri == null || treeUri.isEmpty()) {
                saveLocationValue.setText(getString(R.string.save_location_active_path_format, getString(R.string.save_location_not_selected)));
                saveLocationStatus.setText(R.string.save_location_status_custom_missing);
                saveLocationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            } else {
                saveLocationValue.setText(getString(R.string.save_location_active_path_format, customFolderLabel));
                if (hasPermission) {
                    saveLocationStatus.setText(R.string.save_location_status_custom_ok);
                    saveLocationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                } else {
                    saveLocationStatus.setText(R.string.save_location_status_custom_revoked);
                    saveLocationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                }
            }
        } else {
            saveLocationToggleGroup.check(R.id.save_location_default);
            pickFolderButton.setEnabled(false);
            saveLocationValue.setText(getString(R.string.save_location_active_path_format, relativePath));
            saveLocationStatus.setText(R.string.save_location_status_default);
            saveLocationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        }
    }

    private boolean hasWritePermission(String treeUriString) {
        if (treeUriString == null || treeUriString.isEmpty()) {
            return false;
        }
        Uri treeUri;
        try {
            treeUri = Uri.parse(treeUriString);
        } catch (Exception e) {
            return false;
        }
        for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(treeUri) && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    private String getCustomFolderLabel(String treeUriString) {
        try {
            Uri treeUri = Uri.parse(treeUriString);
            String treeId = DocumentsContract.getTreeDocumentId(treeUri);
            int splitIndex = treeId.indexOf(':');
            if (splitIndex >= 0 && splitIndex + 1 < treeId.length()) {
                String volume = treeId.substring(0, splitIndex);
                String path = treeId.substring(splitIndex + 1);
                if (path.isEmpty()) {
                    return volume;
                }
                return volume + "/" + path;
            }
            return treeId;
        } catch (Exception e) {
            return getString(R.string.save_location_unknown_folder);
        }
    }

    private void updateHistoryLimit() {
        if (isBound && service != null) {
            TimeFormat.Result timeFormatResult = new TimeFormat.Result();
            float historyInSeconds = service.getBytesToSeconds() * service.getMemorySize();
            TimeFormat.naturalLanguage(getResources(), historyInSeconds, timeFormatResult);
            historyLimitTextView.setText(timeFormatResult.text);
            updateSummaryPanel();
        }
    }

    private int getAutoSaveMaxFiles() {
        int maxFiles = sharedPreferences.getInt(AUTO_SAVE_MAX_FILES_KEY, AUTO_SAVE_MAX_FILES_DEFAULT);
        int normalized = Math.max(1, Math.min(100, maxFiles));
        if (maxFiles != normalized) {
            sharedPreferences.edit().putInt(AUTO_SAVE_MAX_FILES_KEY, normalized).apply();
        }
        return normalized;
    }

    private void updateAutoSaveMaxFilesValue(int maxFiles) {
        autoSaveMaxFilesValue.setText(getString(R.string.auto_save_max_files_value, maxFiles));
    }

    private void updateAutoSaveEstimatedDuration() {
        if (!isBound || service == null) {
            return;
        }
        boolean autoSaveEnabled = sharedPreferences.getBoolean("auto_save_enabled", false);
        if (!autoSaveEnabled) {
            autoSaveEstimatedDuration.setText(R.string.auto_save_estimated_history_disabled);
            return;
        }
        int maxFiles = getAutoSaveMaxFiles();
        long totalSeconds = service.getEstimatedAutoSaveHistorySeconds(maxFiles);
        TimeFormat.Result result = new TimeFormat.Result();
        TimeFormat.naturalLanguage(getResources(), totalSeconds, result);
        autoSaveEstimatedDuration.setText(getString(R.string.auto_save_estimated_history_value, result.text));
    }

    private void updateExportSettingsUI(String format) {
        if ("wav".equals(format)) {
            bitrateContainer.setVisibility(android.view.View.GONE);
            bitDepthContainer.setVisibility(android.view.View.VISIBLE);
        } else {
            bitrateContainer.setVisibility(android.view.View.VISIBLE);
            bitDepthContainer.setVisibility(android.view.View.GONE);
        }
        updateEstimatedSize();
    }

    private void updateEstimatedSize() {
        if (!isBound || service == null) return;
        long bytesPerMinute = getEstimatedBytesPerMinute();
        estimatedSizeText.setText(getString(R.string.estimated_size_per_minute, StringFormat.shortFileSize(bytesPerMinute)));
    }

    private long getEstimatedBytesPerMinute() {
        if (!isBound || service == null) {
            return 0;
        }

        String format = sharedPreferences.getString("export_format", "wav");
        if ("wav".equals(format)) {
            int depth = sharedPreferences.getInt("export_bit_depth", 16);
            int sampleRate = service.getSamplingRate();
            int channels = 1;
            long bytesPerSecond = (long) sampleRate * channels * (depth / 8);
            return bytesPerSecond * 60;
        }

        int bitrate = sharedPreferences.getInt("export_bitrate", 32000);
        bitrate = Math.max(8000, Math.min(64000, bitrate));
        long bytesPerSecond = bitrate / 8;
        return bytesPerSecond * 60;
    }

    private void updateSummaryPanel() {
        if (!isBound || service == null) {
            return;
        }

        TimeFormat.Result timeFormatResult = new TimeFormat.Result();
        float historyInSeconds = service.getBytesToSeconds() * service.getMemorySize();
        TimeFormat.naturalLanguage(getResources(), historyInSeconds, timeFormatResult);
        summaryBuffer.setText(getString(R.string.summary_buffer_label) + ": " + timeFormatResult.text);

        int qualityKhz = service.getSamplingRate() / 1000;
        summaryQuality.setText(getString(R.string.summary_quality_label) + ": " + getString(R.string.summary_quality_value, qualityKhz));

        long bytesPerMinute = getEstimatedBytesPerMinute();
        summarySize.setText(getString(R.string.summary_size_label) + ": " + getString(R.string.summary_size_value, StringFormat.shortFileSize(bytesPerMinute)));

        boolean autoSaveEnabled = sharedPreferences.getBoolean("auto_save_enabled", false);
        if (!autoSaveEnabled) {
            summaryAutosave.setText(getString(R.string.summary_autosave_label) + ": " + getString(R.string.summary_autosave_disabled));
        } else {
            int maxFiles = getAutoSaveMaxFiles();
            long totalSeconds = service.getEstimatedAutoSaveHistorySeconds(maxFiles);
            TimeFormat.Result autoSaveFormatResult = new TimeFormat.Result();
            TimeFormat.naturalLanguage(getResources(), totalSeconds, autoSaveFormatResult);
            summaryAutosave.setText(getString(R.string.summary_autosave_label) + ": " + getString(R.string.summary_autosave_value, maxFiles, autoSaveFormatResult.text));
        }

        summaryStability.setText(getString(R.string.summary_stability_label) + ": " + getStabilityLabel());
    }

    private String getStabilityLabel() {
        int score = 0;
        int sampleRate = service.getSamplingRate();
        long memorySize = service.getMemorySize();

        if (memorySize >= AUDIO_MEMORY_PRESETS[3]) {
            score += 2;
        } else if (memorySize >= AUDIO_MEMORY_PRESETS[2]) {
            score += 1;
        }

        if (sampleRate >= 48000) {
            score += 2;
        } else if (sampleRate >= 16000) {
            score += 1;
        }

        if (score >= 4) {
            return getString(R.string.summary_stability_low);
        }
        if (score >= 2) {
            return getString(R.string.summary_stability_medium);
        }
        return getString(R.string.summary_stability_high);
    }
}
