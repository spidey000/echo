package eu.mrogalski.saidit;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SaidItServiceAutoSaveTest {

    @Rule
    public final ServiceTestRule serviceRule = new ServiceTestRule();

    private Context context;
    private SharedPreferences sharedPreferences;
    private List<Uri> createdUris = new ArrayList<>();

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        sharedPreferences = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        // Ensure the service is in a listening state for the test
        sharedPreferences.edit().putBoolean(SaidIt.AUDIO_MEMORY_ENABLED_KEY, true).apply();
    }

    @After
    public void tearDown() {
        // Clean up preferences and any created files after each test
        sharedPreferences.edit().clear().apply();
        ContentResolver contentResolver = context.getContentResolver();
        for (Uri uri : createdUris) {
            try {
                contentResolver.delete(uri, null, null);
            } catch (Exception e) {
                // Log or handle error if cleanup fails
            }
        }
        createdUris.clear();
    }

    @Test
    public void testAutoSave_createsAudioFile() throws Exception {
        // 1. Configure auto-save to be enabled
        sharedPreferences.edit()
                .putBoolean("auto_save_enabled", true)
                .putInt("auto_save_duration", 2) // 2 seconds, although we trigger it manually
                .apply();

        // 2. Start the service.
        Intent serviceIntent = new Intent(context, SaidItService.class);
        serviceRule.startService(serviceIntent);

        // 3. Directly trigger the auto-save action.
        Intent autoSaveIntent = new Intent(context, SaidItService.class);
        autoSaveIntent.setAction("eu.mrogalski.saidit.ACTION_AUTO_SAVE");
        serviceRule.startService(autoSaveIntent);


        // 4. Poll MediaStore for the new file with a timeout.
        ContentResolver contentResolver = context.getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED};
        String selection = MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = new String[]{"Auto-save_%"};
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        Cursor cursor = null;
        boolean fileFound = false;
        long startTime = System.currentTimeMillis();
        long timeout = 10000; // 10 seconds timeout

        while (System.currentTimeMillis() - startTime < timeout) {
            cursor = contentResolver.query(collection, projection, selection, selectionArgs, sortOrder);
            if (cursor != null && cursor.moveToFirst()) {
                fileFound = true;
                break;
            }
            if (cursor != null) {
                cursor.close();
            }
            Thread.sleep(500); // Poll every 500ms
        }


        assertNotNull("Cursor should not be null", cursor);
        assertTrue("A new auto-saved file should be found in MediaStore.", fileFound);

        // 5. Get the URI and add it to the list for cleanup.
        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        long id = cursor.getLong(idColumn);
        Uri contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
        createdUris.add(contentUri);

        cursor.close();
    }
}
