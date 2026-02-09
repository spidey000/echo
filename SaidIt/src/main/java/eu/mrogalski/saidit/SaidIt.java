package eu.mrogalski.saidit;

public class SaidIt {

    static final long MB = 1024L * 1024L;
    static final String PACKAGE_NAME = "com.spidey000.echofork";
    static final String AUDIO_MEMORY_ENABLED_KEY = "audio_memory_enabled";
    static final String AUDIO_MEMORY_SIZE_KEY = "audio_memory_size";
    static final String SAMPLE_RATE_KEY = "sample_rate";
    static final long[] AUDIO_MEMORY_PRESETS = new long[]{32 * MB, 64 * MB, 128 * MB, 256 * MB};
    static final String AUTO_SAVE_MAX_FILES_KEY = "auto_save_max_files";
    static final int AUTO_SAVE_MAX_FILES_DEFAULT = 20;
    static final String AUTO_SAVE_TRACKED_ENTRIES_KEY = "auto_save_tracked_entries";
    static final String SAVE_PATH_MODE_KEY = "save_path_mode";
    static final String SAVE_TREE_URI_KEY = "save_tree_uri";
    static final String SAVE_RELATIVE_PATH_KEY = "save_relative_path";
    static final String SAVE_PATH_MODE_DEFAULT = "default_music";
    static final String SAVE_PATH_MODE_CUSTOM = "custom_tree";
    static final String DEFAULT_SAVE_RELATIVE_PATH = "Music/Audio Memory";
    static final String SKU = "unlimited_history";
    static final String BASE64_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlD0FMFGp4AWzjW" +
            "LTsUZgm0soga0mVVNGFj0qoATaoQCE/LamF7yrMCIFm9sEOB1guCEhzdr16sjysrVc2EPRisS83FoJ4K0R8" +
            "XPDP2TrVT2SAeQpTCG27NNH+W86SlGEqQeQhMPMhR+HDTckHv3KBpD8BZEEIbkXPv6SGFqcZub6xzn9r14l" +
            "6ptYIWboKGGBh1i9/nJpdhCMPxuLn/WZnRXGxqGpfNw2xT25/muUDZgRVezy6/5eI+ciMn5H1U0ADBjXvl1" +
            "Py+4ClkR1V1Mfo9lvauB03zM8Fsa3LlIPle5a+wGKsRCLW/rJ/eE/rje6X7x/n+w8J4OiFvVATj0T8QIDAQ" +
            "AB";

    static long nearestAudioMemoryPreset(long value) {
        long nearest = AUDIO_MEMORY_PRESETS[0];
        long smallestDistance = Math.abs(value - nearest);
        for (long preset : AUDIO_MEMORY_PRESETS) {
            long distance = Math.abs(value - preset);
            if (distance < smallestDistance) {
                smallestDistance = distance;
                nearest = preset;
            }
        }
        return nearest;
    }

}
