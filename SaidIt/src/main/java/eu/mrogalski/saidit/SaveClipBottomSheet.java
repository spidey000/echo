package eu.mrogalski.saidit;

import static eu.mrogalski.saidit.SaidIt.PACKAGE_NAME;
import static eu.mrogalski.saidit.SaidIt.SAMPLE_RATE_KEY;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;

import eu.mrogalski.StringFormat;
import eu.mrogalski.android.TimeFormat;

public class SaveClipBottomSheet extends BottomSheetDialogFragment {

    public interface SaveClipListener {
        void onSaveClip(String fileName, float durationInSeconds, String format, int bitrate, int bitDepth);
    }

    private static final String ARG_MEMORIZED_DURATION = "memorized_duration";
    private float memorizedDuration;
    private SaveClipListener listener;
    private SharedPreferences sharedPreferences;

    public static SaveClipBottomSheet newInstance(float memorizedDuration) {
        SaveClipBottomSheet fragment = new SaveClipBottomSheet();
        Bundle args = new Bundle();
        args.putFloat(ARG_MEMORIZED_DURATION, memorizedDuration);
        fragment.setArguments(args);
        return fragment;
    }

    public void setSaveClipListener(SaveClipListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            memorizedDuration = getArguments().getFloat(ARG_MEMORIZED_DURATION);
        }
        if (getContext() != null) {
            sharedPreferences = getContext().getSharedPreferences(PACKAGE_NAME, android.content.Context.MODE_PRIVATE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_save_clip, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final TextInputEditText fileNameInput = view.findViewById(R.id.recording_name);
        final ChipGroup durationChipGroup = view.findViewById(R.id.duration_chip_group);
        final Chip durationAllChip = view.findViewById(R.id.duration_all);
        final MaterialButton saveButton = view.findViewById(R.id.save_button);
        final MaterialButtonToggleGroup formatToggleGroup = view.findViewById(R.id.export_format_toggle_group);
        final LinearLayout bitrateContainer = view.findViewById(R.id.bitrate_container);
        final Slider bitrateSlider = view.findViewById(R.id.bitrate_slider);
        final TextView bitrateValue = view.findViewById(R.id.bitrate_value);
        final LinearLayout bitDepthContainer = view.findViewById(R.id.bit_depth_container);
        final MaterialButtonToggleGroup bitDepthToggleGroup = view.findViewById(R.id.bit_depth_toggle_group);
        final TextView estimatedSizeText = view.findViewById(R.id.estimated_size_text);
        
        // Update the "All memory" chip with the actual duration
        durationAllChip.setText(getString(R.string.all_memory) + " (" + TimeFormat.shortTimer(memorizedDuration) + ")");

        // Set default selection
        ((Chip)view.findViewById(R.id.duration_1m)).setChecked(true);

        String defaultFormat = sharedPreferences != null ? sharedPreferences.getString("export_format", "wav") : "wav";
        int defaultBitrate = sharedPreferences != null ? sharedPreferences.getInt("export_bitrate", 32000) : 32000;
        defaultBitrate = Math.max(8000, Math.min(64000, defaultBitrate));
        int defaultBitDepth = sharedPreferences != null ? sharedPreferences.getInt("export_bit_depth", 16) : 16;

        if ("mp3".equals(defaultFormat)) {
            formatToggleGroup.check(R.id.format_mp3);
        } else if ("opus".equals(defaultFormat)) {
            formatToggleGroup.check(R.id.format_opus);
        } else {
            formatToggleGroup.check(R.id.format_wav);
        }

        bitrateSlider.setValue(defaultBitrate);
        bitrateValue.setText((defaultBitrate / 1000) + " kbps");

        if (defaultBitDepth == 24) {
            bitDepthToggleGroup.check(R.id.depth_24);
        } else if (defaultBitDepth == 32) {
            bitDepthToggleGroup.check(R.id.depth_32);
        } else {
            bitDepthToggleGroup.check(R.id.depth_16);
        }

        final Runnable updateEstimatedSize = () -> {
            String format = getSelectedFormat(formatToggleGroup.getCheckedButtonId());
            long bytesPerMinute;
            if ("wav".equals(format)) {
                int bitDepth = getSelectedBitDepth(bitDepthToggleGroup.getCheckedButtonId());
                int sampleRate = sharedPreferences != null ? sharedPreferences.getInt(SAMPLE_RATE_KEY, 16000) : 16000;
                bytesPerMinute = (long) sampleRate * (bitDepth / 8) * 60;
            } else {
                bytesPerMinute = ((long) bitrateSlider.getValue() / 8L) * 60L;
            }
            estimatedSizeText.setText(getString(R.string.estimated_size_per_minute, StringFormat.shortFileSize(bytesPerMinute)));
        };

        formatToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            String format = getSelectedFormat(checkedId);
            if ("wav".equals(format)) {
                bitrateContainer.setVisibility(View.GONE);
                bitDepthContainer.setVisibility(View.VISIBLE);
            } else {
                bitrateContainer.setVisibility(View.VISIBLE);
                bitDepthContainer.setVisibility(View.GONE);
            }
            updateEstimatedSize.run();
        });

        bitrateSlider.addOnChangeListener((slider, value, fromUser) -> {
            int bitrate = (int) value;
            bitrateValue.setText((bitrate / 1000) + " kbps");
            updateEstimatedSize.run();
        });

        bitDepthToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> updateEstimatedSize.run());

        if ("wav".equals(defaultFormat)) {
            bitrateContainer.setVisibility(View.GONE);
            bitDepthContainer.setVisibility(View.VISIBLE);
        } else {
            bitrateContainer.setVisibility(View.VISIBLE);
            bitDepthContainer.setVisibility(View.GONE);
        }
        updateEstimatedSize.run();

        saveButton.setOnClickListener(v -> {
            String fileName = fileNameInput.getText() != null ? fileNameInput.getText().toString().trim() : "";
            if (fileName.isEmpty()) {
                Toast.makeText(getContext(), R.string.please_enter_file_name, Toast.LENGTH_SHORT).show();
                return;
            }

            int checkedChipId = durationChipGroup.getCheckedChipId();
            float durationInSeconds = 0;

            if (checkedChipId == R.id.duration_1m) {
                durationInSeconds = 60;
            } else if (checkedChipId == R.id.duration_5m) {
                durationInSeconds = 300;
            } else if (checkedChipId == R.id.duration_30m) {
                durationInSeconds = 1800;
            } else if (checkedChipId == R.id.duration_all) {
                durationInSeconds = memorizedDuration;
            }

            String format = getSelectedFormat(formatToggleGroup.getCheckedButtonId());
            int bitrate = (int) bitrateSlider.getValue();
            int bitDepth = getSelectedBitDepth(bitDepthToggleGroup.getCheckedButtonId());

            if (listener != null) {
                listener.onSaveClip(fileName, durationInSeconds, format, bitrate, bitDepth);
            }
            dismiss();
        });
    }

    private String getSelectedFormat(int checkedId) {
        if (checkedId == R.id.format_mp3) {
            return "mp3";
        }
        if (checkedId == R.id.format_opus) {
            return "opus";
        }
        return "wav";
    }

    private int getSelectedBitDepth(int checkedId) {
        if (checkedId == R.id.depth_24) {
            return 24;
        }
        if (checkedId == R.id.depth_32) {
            return 32;
        }
        return 16;
    }
}
