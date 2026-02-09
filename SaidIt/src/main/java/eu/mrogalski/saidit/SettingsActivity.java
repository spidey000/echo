package eu.mrogalski.saidit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import static eu.mrogalski.saidit.SaidIt.PACKAGE_NAME;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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
    private Button memoryLowButton, memoryMediumButton, memoryHighButton;
    private Button quality8kHzButton, quality16kHzButton, quality48kHzButton;
    private SwitchMaterial autoSaveSwitch;
    private SwitchMaterial noiseSuppressorSwitch;
    private SwitchMaterial automaticGainControlSwitch;
    private Slider autoSaveDurationSlider;
    private TextView autoSaveDurationLabel;

    private MaterialButtonToggleGroup exportFormatToggleGroup;
    private LinearLayout bitrateContainer;
    private Slider bitrateSlider;
    private TextView bitrateValue;
    private LinearLayout bitDepthContainer;
    private MaterialButtonToggleGroup bitDepthToggleGroup;
    private TextView estimatedSizeText;

    private SharedPreferences sharedPreferences;

    private boolean isBound = false;

    private final MaterialButtonToggleGroup.OnButtonCheckedListener memoryToggleListener = (group, checkedId, isChecked) -> {
        if (isChecked && isBound) {
            final long maxMemory = Runtime.getRuntime().maxMemory();
            long memorySize = maxMemory / 4; // Default to low
            if (checkedId == R.id.memory_medium) {
                memorySize = maxMemory / 2;
            } else if (checkedId == R.id.memory_high) {
                memorySize = (long) (maxMemory * 0.90);
            }
            service.setMemorySize(memorySize);
            updateHistoryLimit();
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
        memoryLowButton = findViewById(R.id.memory_low);
        memoryMediumButton = findViewById(R.id.memory_medium);
        memoryHighButton = findViewById(R.id.memory_high);
        quality8kHzButton = findViewById(R.id.quality_8kHz);
        quality16kHzButton = findViewById(R.id.quality_16kHz);
        quality48kHzButton = findViewById(R.id.quality_48kHz);
        autoSaveSwitch = findViewById(R.id.auto_save_switch);
        noiseSuppressorSwitch = findViewById(R.id.noise_suppressor_switch);
        automaticGainControlSwitch = findViewById(R.id.automatic_gain_control_switch);
        autoSaveDurationSlider = findViewById(R.id.auto_save_duration_slider);
        autoSaveDurationLabel = findViewById(R.id.auto_save_duration_label);

        exportFormatToggleGroup = findViewById(R.id.export_format_toggle_group);
        bitrateContainer = findViewById(R.id.bitrate_container);
        bitrateSlider = findViewById(R.id.bitrate_slider);
        bitrateValue = findViewById(R.id.bitrate_value);
        bitDepthContainer = findViewById(R.id.bit_depth_container);
        bitDepthToggleGroup = findViewById(R.id.bit_depth_toggle_group);
        estimatedSizeText = findViewById(R.id.estimated_size_text);

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

        noiseSuppressorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("noise_suppressor_enabled", isChecked).apply();
            if (isBound) {
                service.setSampleRate(service.getSamplingRate());
            }
        });

        automaticGainControlSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("automatic_gain_control_enabled", isChecked).apply();
            if (isBound) {
                service.setSampleRate(service.getSamplingRate());
            }
        });

        autoSaveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("auto_save_enabled", isChecked).apply();
            autoSaveDurationSlider.setEnabled(isChecked);
            autoSaveDurationLabel.setEnabled(isChecked);
            if (isBound) {
            }
        });

        autoSaveDurationSlider.addOnChangeListener((slider, value, fromUser) -> {
            int minutes = (int) value;
            updateAutoSaveLabel(minutes);
            if (fromUser) {
                sharedPreferences.edit().putInt("auto_save_duration", minutes * 60).apply();
            }
        });

        exportFormatToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                String format = "wav";
                if (checkedId == R.id.format_mp3) format = "mp3";
                else if (checkedId == R.id.format_opus) format = "opus";

                sharedPreferences.edit().putString("export_format", format).apply();
                updateExportSettingsUI(format);
            }
        });

        bitrateSlider.addOnChangeListener((slider, value, fromUser) -> {
            int bitrate = (int) value;
            bitrateValue.setText((bitrate / 1000) + " kbps");
            if (fromUser) {
                sharedPreferences.edit().putInt("export_bitrate", bitrate).apply();
            }
            updateEstimatedSize();
        });

        bitDepthToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                int depth = 16;
                if (checkedId == R.id.depth_24) depth = 24;
                else if (checkedId == R.id.depth_32) depth = 32;

                sharedPreferences.edit().putInt("export_bit_depth", depth).apply();
            }
            updateEstimatedSize();
        });
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

        // Set memory button text
        final long maxMemory = Runtime.getRuntime().maxMemory();
        memoryLowButton.setText(StringFormat.shortFileSize(maxMemory / 4));
        memoryMediumButton.setText(StringFormat.shortFileSize(maxMemory / 2));
        memoryHighButton.setText(StringFormat.shortFileSize((long) (maxMemory * 0.90)));

        // Set memory button state
        long currentMemory = service.getMemorySize();
        if (currentMemory <= maxMemory / 4) {
            memoryToggleGroup.check(R.id.memory_low);
        } else if (currentMemory <= maxMemory / 2) {
            memoryToggleGroup.check(R.id.memory_medium);
        } else {
            memoryToggleGroup.check(R.id.memory_high);
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
        autoSaveDurationSlider.setEnabled(autoSaveEnabled);
        autoSaveDurationLabel.setEnabled(autoSaveEnabled);

        int autoSaveDurationSeconds = sharedPreferences.getInt("auto_save_duration", 600); // Default to 10 minutes
        int autoSaveDurationMinutes = autoSaveDurationSeconds / 60;
        autoSaveDurationSlider.setValue(autoSaveDurationMinutes);
        updateAutoSaveLabel(autoSaveDurationMinutes);

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

        // Re-add listeners
        memoryToggleGroup.addOnButtonCheckedListener(memoryToggleListener);
        qualityToggleGroup.addOnButtonCheckedListener(qualityToggleListener);
        
        boolean noiseSuppressorEnabled = sharedPreferences.getBoolean("noise_suppressor_enabled", false);
        noiseSuppressorSwitch.setChecked(noiseSuppressorEnabled);

        boolean automaticGainControlEnabled = sharedPreferences.getBoolean("automatic_gain_control_enabled", false);
        automaticGainControlSwitch.setChecked(automaticGainControlEnabled);
    }

    private void updateHistoryLimit() {
        if (isBound && service != null) {
            TimeFormat.Result timeFormatResult = new TimeFormat.Result();
            float historyInSeconds = service.getBytesToSeconds() * service.getMemorySize();
            TimeFormat.naturalLanguage(getResources(), historyInSeconds, timeFormatResult);
            historyLimitTextView.setText(timeFormatResult.text);
        }
    }

    private void updateAutoSaveLabel(int totalMinutes) {
        if (totalMinutes < 60) {
            autoSaveDurationLabel.setText(getResources().getQuantityString(R.plurals.minute_plural, totalMinutes, totalMinutes));
        } else {
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            String hourText = getResources().getQuantityString(R.plurals.hour_plural, hours, hours);
            if (minutes == 0) {
                autoSaveDurationLabel.setText(hourText);
            } else {
                String minuteText = getResources().getQuantityString(R.plurals.minute_plural, minutes, minutes);
                autoSaveDurationLabel.setText(getString(R.string.time_join, hourText, minuteText));
            }
        }
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

        long bytesPerMinute = 0;
        String format = sharedPreferences.getString("export_format", "wav");

        if ("wav".equals(format)) {
            int depth = sharedPreferences.getInt("export_bit_depth", 16);
            int sampleRate = service.getSamplingRate();
            int channels = 1; // Assuming Mono
            // Bytes per second = SampleRate * Channels * (Depth / 8)
            long bytesPerSecond = (long)sampleRate * channels * (depth / 8);
            bytesPerMinute = bytesPerSecond * 60;
        } else {
            // MP3/Opus - Bitrate is in bits per second, we want bytes per minute
            int bitrate = sharedPreferences.getInt("export_bitrate", 32000);
            bitrate = Math.max(8000, Math.min(64000, bitrate));
            // Bytes per second = Bitrate / 8
            long bytesPerSecond = bitrate / 8;
            bytesPerMinute = bytesPerSecond * 60;
        }

        // Need to format bytesPerMinute properly
        estimatedSizeText.setText(getString(R.string.estimated_size_per_minute, StringFormat.shortFileSize(bytesPerMinute)));
    }
}
