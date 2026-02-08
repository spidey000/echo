// SafeFileManager.java
package eu.mrogalski.saidit.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

public class SafeFileManager implements Closeable {
    private static final String TAG = "SafeFileManager";
    private final List<AutoCloseable> resources = new ArrayList<>();
    private final List<File> tempFiles = new ArrayList<>();
    
    public void register(AutoCloseable resource) {
        synchronized (resources) {
            resources.add(resource);
        }
    }
    
    public void registerTempFile(File file) {
        synchronized (tempFiles) {
            tempFiles.add(file);
        }
    }
    
    @Override
    public void close() {
        synchronized (resources) {
            for (AutoCloseable resource : resources) {
                try {
                    resource.close();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to close resource", e);
                }
            }
            resources.clear();
        }
        
        synchronized (tempFiles) {
            for (File file : tempFiles) {
                if (file.exists() && !file.delete()) {
                    file.deleteOnExit();
                }
            }
            tempFiles.clear();
        }
        
        // Force garbage collection to release file handles
        System.gc();
        System.runFinalization();
    }
}
