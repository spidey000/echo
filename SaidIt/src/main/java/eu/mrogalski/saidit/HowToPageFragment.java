package eu.mrogalski.saidit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class HowToPageFragment extends Fragment {

    private static final String ARG_POSITION = "position";

    public static HowToPageFragment newInstance(int position) {
        HowToPageFragment fragment = new HowToPageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_how_to_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView textView = view.findViewById(R.id.how_to_text);
        int position = getArguments().getInt(ARG_POSITION);
        // Set text based on position
        switch (position) {
            case 0:
                textView.setText("Step 1: Press the record button to start saving audio.");
                break;
            case 1:
                textView.setText("Step 2: Press the save button to save the last few minutes of audio.");
                break;
            case 2:
                textView.setText("Step 3: Access your saved recordings from the recordings manager.");
                break;
        }
    }
}
