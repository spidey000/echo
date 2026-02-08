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
}
