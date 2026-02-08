package eu.mrogalski.saidit;

import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordingsActivity extends AppCompatActivity {

    private RecordingsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recordings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recordings_recycler_view);
        TextView emptyView = findViewById(R.id.empty_view);

        // Load recordings and set up adapter
        List<RecordingItem> recordings = getRecordings();
        adapter = new RecordingsAdapter(this, recordings);
        recyclerView.setAdapter(adapter);

        if (recordings.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private List<RecordingItem> getRecordings() {
        List<RecordingItem> recordingItems = new ArrayList<>();
        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DURATION
        };
        String selection = MediaStore.Audio.Media.MIME_TYPE + " IN (?, ?, ?)";
        String[] selectionArgs = new String[]{"audio/mp4", "audio/m4a", "audio/aac"};
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getApplicationContext().getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    long date = cursor.getLong(dateColumn);
                    long duration = cursor.getLong(durationColumn);
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    recordingItems.add(new RecordingItem(contentUri, name, date, duration));
                }
            }
        }
        return recordingItems;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adapter != null) {
            adapter.releasePlayer();
        }
    }
}
