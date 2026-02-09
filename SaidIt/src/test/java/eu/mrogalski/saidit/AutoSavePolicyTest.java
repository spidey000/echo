package eu.mrogalski.saidit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AutoSavePolicyTest {

    @Test
    public void noRotationWhenUnderLimit() {
        List<SaidItService.AutoSaveEntry> entries = new ArrayList<>();
        entries.add(new SaidItService.AutoSaveEntry("content://a", "Echo_auto_1", 1L));
        entries.add(new SaidItService.AutoSaveEntry("content://b", "Echo_auto_2", 2L));

        List<SaidItService.AutoSaveEntry> toRotate = AutoSavePolicy.selectAutoEntriesToRotate(entries, 3);

        assertTrue(toRotate.isEmpty());
    }

    @Test
    public void rotationRemovesOldestAutoEntriesWhenOverLimit() {
        List<SaidItService.AutoSaveEntry> entries = new ArrayList<>();
        entries.add(new SaidItService.AutoSaveEntry("content://a", "Echo_auto_old", 1L));
        entries.add(new SaidItService.AutoSaveEntry("content://b", "Echo_auto_mid", 2L));
        entries.add(new SaidItService.AutoSaveEntry("content://c", "Echo_auto_new", 3L));

        List<SaidItService.AutoSaveEntry> toRotate = AutoSavePolicy.selectAutoEntriesToRotate(entries, 2);

        assertEquals(1, toRotate.size());
        assertEquals("content://a", toRotate.get(0).uri);
    }

    @Test
    public void manualEntriesAreNeverSelectedForRotation() {
        List<SaidItService.AutoSaveEntry> entries = new ArrayList<>();
        entries.add(new SaidItService.AutoSaveEntry("content://manual", "Meeting_recording", 1L));
        entries.add(new SaidItService.AutoSaveEntry("content://auto1", "Echo_auto_1", 2L));
        entries.add(new SaidItService.AutoSaveEntry("content://auto2", "Echo_auto_2", 3L));

        List<SaidItService.AutoSaveEntry> toRotate = AutoSavePolicy.selectAutoEntriesToRotate(entries, 1);

        assertEquals(1, toRotate.size());
        assertEquals("content://auto1", toRotate.get(0).uri);
    }

    @Test
    public void estimatedDurationUsesBufferTimesLimit() {
        long memoryBytes = 128L * 1024L * 1024L;
        float bytesToSeconds = 1f / 32000f;

        long estimated = AutoSavePolicy.estimatedTotalHistorySeconds(memoryBytes, bytesToSeconds, 20);

        assertEquals(Math.round(memoryBytes * bytesToSeconds * 20f), estimated);
    }
}
