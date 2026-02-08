package eu.mrogalski.saidit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SaidItServiceTest {

    @Mock
    private Context mockContext;
    @Mock
    private SharedPreferences mockPrefs;
    @Mock
    private SharedPreferences.Editor mockEditor;
    @Mock
    private Handler mockAudioHandler;

    @InjectMocks
    private SaidItService saidItService;

    @Before
    public void setUp() {
        // Mock Android dependencies
        when(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor);
        when(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor);

        // This allows us to immediately run Runnables posted to the handler
        when(mockAudioHandler.post(any(Runnable.class))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            }
        });

        // Use the mocked handler
        saidItService.audioHandler = mockAudioHandler;
    }
    
    @Test
    public void testInitialState() {
        assertEquals(SaidItService.STATE_READY, saidItService.state);
    }

    @Test
    public void testEnableListening_changesState() {
        // Given the service is in the READY state
        saidItService.state = SaidItService.STATE_READY;

        // When listening is enabled
        saidItService.enableListening();

        // Then the state transitions to LISTENING
        assertEquals(SaidItService.STATE_LISTENING, saidItService.state);
    }
    
    @Test
    public void testDisableListening_changesState() {
        // Given the service is listening
        saidItService.state = SaidItService.STATE_LISTENING;

        // When listening is disabled
        saidItService.disableListening();

        // Then the state transitions back to READY
        assertEquals(SaidItService.STATE_READY, saidItService.state);
    }
    
    @Test
    public void testStartRecording_changesState() {
        // Given the service is listening
        saidItService.state = SaidItService.STATE_LISTENING;
        
        // When recording is started
        saidItService.startRecording(5.0f);
        
        // Then the state transitions to RECORDING
        assertEquals(SaidItService.STATE_RECORDING, saidItService.state);
    }
    
    @Test
    public void testStopRecording_changesState() {
        // Given the service is recording
        saidItService.state = SaidItService.STATE_RECORDING;

        // When recording is stopped
        saidItService.stopRecording(null);

        // Then the state transitions back to LISTENING
        assertEquals(SaidItService.STATE_LISTENING, saidItService.state);
    }
    
    @Test
    public void testDumpRecording_callsAudioMemoryDump() throws IOException {
        // Given the service is listening
        saidItService.state = SaidItService.STATE_LISTENING;
        saidItService.SAMPLE_RATE = 48000;
        saidItService.FILL_RATE = 2 * saidItService.SAMPLE_RATE;
        
        // When dumpRecording is called
        saidItService.dumpRecording(10, null, "test_dump");
        
        // Then it should have posted a runnable to the audioHandler
        verify(saidItService.audioHandler).post(any(Runnable.class));
    }
}