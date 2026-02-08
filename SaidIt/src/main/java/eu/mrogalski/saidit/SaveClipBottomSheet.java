package eu.mrogalski.saidit;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import eu.mrogalski.android.TimeFormat;

public class SaveClipBottomSheet extends BottomSheetDialogFragment {

    public interface SaveClipListener {
        void onSaveClip(String fileName, float durationInSeconds);
    }

    private static final String ARG_MEMORIZED_DURATION = "memorized_duration";
    private float memorizedDuration;
    private SaveClipListener listener;

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
        
        // Update the "All memory" chip with the actual duration
        durationAllChip.setText(getString(R.string.all_memory) + " (" + TimeFormat.shortTimer(memorizedDuration) + ")");

        // Set default selection
        ((Chip)view.findViewById(R.id.duration_1m)).setChecked(true);

        saveButton.setOnClickListener(v -> {
            String fileName = fileNameInput.getText() != null ? fileNameInput.getText().toString().trim() : "";
            if (fileName.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a file name", Toast.LENGTH_SHORT).show();
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

            if (listener != null) {
                listener.onSaveClip(fileName, durationInSeconds);
            }
            dismiss();
        });
    }
}
