package eu.mrogalski.saidit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import eu.mrogalski.saidit.util.SafeFileManager;

@RunWith(AndroidJUnit4.class)
public class AacMp4WriterTest {

    private SafeFileManager fileManager;
    private AacMp4Writer writer;
    private File testFile;

    @Before
    public void setUp() throws Exception {
        fileManager = new SafeFileManager();
        File cacheDir = InstrumentationRegistry.getInstrumentation().getContext().getCacheDir();
        testFile = new File(cacheDir, "test.m4a");
        if (testFile.exists()) {
            testFile.delete();
        }
        fileManager.registerTempFile(testFile);
    }

    @After
    public void tearDown() throws Exception {
        if (writer != null) {
            writer.close();
            writer = null;
        }
        if (fileManager != null) {
            fileManager.close();
            fileManager = null;
        }
    }

    @Test
    public void testWriteAndClose() throws IOException {
        writer = new AacMp4Writer(48000, 1, 96000, testFile);
        fileManager.register(writer);

        // Test writing a small amount of data
        byte[] testData = new byte[2048]; // Use a realistic buffer size
        writer.write(testData, 0, testData.length);

        // Close should work without issues
        writer.close();
        writer = null; // Prevent double close in tearDown
    }
}
