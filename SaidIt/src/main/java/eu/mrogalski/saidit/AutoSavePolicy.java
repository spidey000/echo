package eu.mrogalski.saidit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class AutoSavePolicy {
    private AutoSavePolicy() {
    }

    static float getFillRatio(AudioMemory.Stats stats) {
        if (stats == null || stats.total <= 0) {
            return 0f;
        }
        if (stats.overwriting) {
            return stats.writePos / (float) stats.total;
        }
        return Math.min(1f, stats.filled / (float) stats.total);
    }

    static long estimatedTotalHistorySeconds(long memoryBytes, float bytesToSeconds, int maxAutoSaves) {
        int safeMax = Math.max(1, maxAutoSaves);
        return Math.round(memoryBytes * bytesToSeconds * safeMax);
    }

    static List<SaidItService.AutoSaveEntry> selectAutoEntriesToRotate(
            List<SaidItService.AutoSaveEntry> allEntries,
            int maxAutoSaves
    ) {
        int safeMax = Math.max(1, maxAutoSaves);
        List<SaidItService.AutoSaveEntry> autoEntries = new ArrayList<>();
        for (SaidItService.AutoSaveEntry entry : allEntries) {
            if (entry != null && entry.isAuto()) {
                autoEntries.add(entry);
            }
        }
        autoEntries.sort(Comparator.comparingLong(entry -> entry.savedAtMs));
        if (autoEntries.size() <= safeMax) {
            return new ArrayList<>();
        }
        return new ArrayList<>(autoEntries.subList(0, autoEntries.size() - safeMax));
    }
}
