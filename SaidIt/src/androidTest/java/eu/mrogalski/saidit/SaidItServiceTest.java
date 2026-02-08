package eu.mrogalski.saidit;

import android.content.Context;
import android.content.Intent;
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

import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class SaidItServiceTest {

    @Rule
    public final ServiceTestRule serviceRule = new ServiceTestRule();

    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences(SaidIt.PACKAGE_NAME, Context.MODE_PRIVATE);
        // Ensure a clean state before each test
        prefs.edit().clear().commit();
    }

    @After
    public void tearDown() {
        // Clean up preferences after each test
        prefs.edit().clear().commit();
    }

    private SaidItService getService() throws TimeoutException {
        Intent serviceIntent = new Intent(context, SaidItService.class);
        IBinder binder = serviceRule.bindService(serviceIntent);
        return ((SaidItService.LocalBinder) binder).getService();
    }

    private void waitForState(SaidItService service, SaidItService.ServiceState expectedState) throws InterruptedException {
        // In a real test environment, the state transition is not always instantaneous.
        // We poll for a short period to allow the service's handler to process the state change.
        int timeout = 2000; // 2 seconds
        int interval = 100; // 100 ms
        int elapsed = 0;
        while (service.state != expectedState && elapsed < timeout) {
            Thread.sleep(interval);
            elapsed += interval;
        }
        assertEquals("Service did not reach the expected state.", expectedState, service.state);
    }


    @Test
    public void testInitialState_startsListeningByDefault() throws TimeoutException, InterruptedException {
        // Enable audio memory before the service is created
        prefs.edit().putBoolean(SaidIt.AUDIO_MEMORY_ENABLED_KEY, true).commit();

        SaidItService service = getService();
        service.mIsTestEnvironment = true;

        waitForState(service, SaidItService.ServiceState.LISTENING);
    }

    @Test
    public void testInitialState_isReadyWhenDisabled() throws TimeoutException, InterruptedException {
        // Audio memory is disabled by default (cleared in setUp)
        SaidItService service = getService();
        service.mIsTestEnvironment = true;
        
        waitForState(service, SaidItService.ServiceState.READY);
    }

    @Test
    public void testEnableListening_changesState() throws TimeoutException, InterruptedException {
        // Start with listening disabled
        prefs.edit().putBoolean(SaidIt.AUDIO_MEMORY_ENABLED_KEY, false).commit();
        SaidItService service = getService();
        service.mIsTestEnvironment = true;
        waitForState(service, SaidItService.ServiceState.READY);

        // When listening is enabled
        service.enableListening();
        
        // Then the state transitions to LISTENING
        waitForState(service, SaidItService.ServiceState.LISTENING);
    }

    @Test
    public void testDisableListening_changesState() throws TimeoutException, InterruptedException {
        // Start with listening enabled
        prefs.edit().putBoolean(SaidIt.AUDIO_MEMORY_ENABLED_KEY, true).commit();
        SaidItService service = getService();
        service.mIsTestEnvironment = true;
        waitForState(service, SaidItService.ServiceState.LISTENING);

        // When listening is disabled
        service.disableListening();

        // Then the state transitions back to READY
        waitForState(service, SaidItService.ServiceState.READY);
    }

    @Test
    public void testStartRecording_changesState() throws TimeoutException, InterruptedException {
        // Given the service is listening
        prefs.edit().putBoolean(SaidIt.AUDIO_MEMORY_ENABLED_KEY, true).commit();
        SaidItService service = getService();
        service.mIsTestEnvironment = true;
        waitForState(service, SaidItService.ServiceState.LISTENING);

        // When recording is started
        service.startRecording(5.0f);

        // Then the state transitions to RECORDING
        waitForState(service, SaidItService.ServiceState.RECORDING);
    }

    @Test
    public void testStopRecording_changesState() throws TimeoutException, InterruptedException {
        // Given the service is recording
        prefs.edit().putBoolean(SaidIt.AUDIO_MEMORY_ENABLED_KEY, true).commit();
        SaidItService service = getService();
        service.mIsTestEnvironment = true;
        waitForState(service, SaidItService.ServiceState.LISTENING);
        service.startRecording(5.0f);
        waitForState(service, SaidItService.ServiceState.RECORDING);

        // When recording is stopped
        service.stopRecording(null);

        // Then the state transitions back to LISTENING
        waitForState(service, SaidItService.ServiceState.LISTENING);
    }
}
