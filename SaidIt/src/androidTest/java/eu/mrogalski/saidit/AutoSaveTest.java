package eu.mrogalski.saidit;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AutoSaveTest {

    @Rule
    public final ServiceTestRule serviceRule = new ServiceTestRule();

    private Context context;
    private SaidItService mService;
    private boolean mBound = false;
    private final CountDownLatch latch = new CountDownLatch(1);


    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() {
        if (mBound) {
            context.unbindService(mConnection);
            mBound = false;
        }
        // Stop the service
        context.stopService(new Intent(context, SaidItService.class));
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            SaidItService.BackgroundRecorderBinder binder = (SaidItService.BackgroundRecorderBinder) service;
            mService = binder.getService();
            mBound = true;
            latch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


    @Test
    public void testAutoSaveDoesNotCrashService() throws TimeoutException, InterruptedException {
        // 1. Configure auto-save
        SharedPreferences preferences = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        preferences.edit()
                .putBoolean("auto_save_enabled", true)
                .putInt("auto_save_duration", 5) // 5 seconds
                .apply();

        // 2. Start and bind to the service
        Intent intent = new Intent(context, SaidItService.class);
        context.startService(intent);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        // Wait for the service to be connected
        assertTrue("Failed to bind to service", latch.await(5, TimeUnit.SECONDS));
        assertNotNull("Service should be bound", mService);


        // 3. Directly trigger the auto-save action.
        Intent autoSaveIntent = new Intent(context, SaidItService.class);
        autoSaveIntent.setAction("eu.mrogalski.saidit.ACTION_AUTO_SAVE");
        context.startService(autoSaveIntent);


        // 4. Give the service some time to process the auto-save
        Thread.sleep(2000);

        // 5. Check if the service is still bound
        assertTrue("Service should still be bound after auto-save", mBound);
    }
}
