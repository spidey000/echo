package eu.mrogalski.saidit;

import eu.mrogalski.saidit.vad.Vad;
import eu.mrogalski.saidit.analysis.SegmentationController;
import eu.mrogalski.saidit.storage.RecordingStoreManager;
import eu.mrogalski.saidit.ml.TfLiteClassifier;
import eu.mrogalski.saidit.vad.EnergyVad;
import eu.mrogalski.saidit.storage.SimpleRecordingStoreManager;
import eu.mrogalski.saidit.analysis.SimpleSegmentationController;
import eu.mrogalski.saidit.ml.AudioEventClassifier;
import eu.mrogalski.saidit.storage.AudioTag;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioProcessingPipeline {
    private static final String TAG = "AudioProcessingPipeline";
    
    private final WeakReference<Context> mContextRef;
    private final int mSampleRate;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    private volatile Vad vad;
    private volatile SegmentationController segmentationController;
    private volatile RecordingStoreManager recordingStoreManager;
    private volatile TfLiteClassifier audioClassifier;
    
    // Reusable buffers to reduce allocations
    private final ThreadLocal<short[]> shortArrayBuffer = new ThreadLocal<>();
    private final ThreadLocal<ByteBuffer> byteBufferCache = new ThreadLocal<>();
    
    public AudioProcessingPipeline(Context context, int sampleRate) {
        // Use weak reference to prevent context leak
        mContextRef = new WeakReference<>(context.getApplicationContext());
        mSampleRate = sampleRate;
    }
    
    public synchronized void start() {
        if (isRunning.get()) {
            Log.w(TAG, "Pipeline already running");
            return;
        }
        
        Context context = mContextRef.get();
        if (context == null) {
            Log.e(TAG, "Context is null, cannot start pipeline");
            return;
        }
        
        try {
            vad = new EnergyVad();
            vad.init(mSampleRate);
            vad.setMode(2);
            
            recordingStoreManager = new SimpleRecordingStoreManager(context, mSampleRate);
            segmentationController = new SimpleSegmentationController(mSampleRate, 16);
            
            // Use weak reference in listener to prevent leak
            final WeakReference<RecordingStoreManager> storeRef = 
                new WeakReference<>(recordingStoreManager);
            
            segmentationController.setListener(new SegmentationController.SegmentListener() {
                @Override
                public void onSegmentStart(long timestamp) {
                    RecordingStoreManager store = storeRef.get();
                    if (store != null) {
                        try {
                            store.onSegmentStart(timestamp);
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to start segment", e);
                        }
                    }
                }
                
                @Override
                public void onSegmentEnd(long timestamp) {
                    RecordingStoreManager store = storeRef.get();
                    if (store != null) {
                        store.onSegmentEnd(timestamp);
                    }
                }
                
                @Override
                public void onSegmentData(byte[] data, int offset, int length) {
                    RecordingStoreManager store = storeRef.get();
                    if (store != null) {
                        store.onSegmentData(data, offset, length);
                    }
                }
            });
            
            isRunning.set(true);
        } catch (Exception criticalError) {
            Log.e(TAG, "Failed to start pipeline (critical)", criticalError);
            stop(); // Clean up partial initialization for critical startup failures
            return;
        }

        try {
            audioClassifier = new AudioEventClassifier();
            audioClassifier.load(context, "yamnet_tiny.tfile", "yamnet_tiny_labels.txt");
        } catch (Exception optionalError) {
            Log.w(TAG, "classifier_unavailable_fallback", optionalError);
            if (audioClassifier != null) {
                try {
                    audioClassifier.close();
                } catch (Exception ignored) {
                }
            }
            audioClassifier = null;
        }
    }
    
    public void process(byte[] audioData, int offset, int length) {
        if (!isRunning.get()) {
            return;
        }
        
        try {
            boolean isSpeech = vad != null && vad.process(audioData, offset, length);
            
            if (segmentationController != null) {
                segmentationController.process(audioData, offset, length, isSpeech);
            }
            
            if (audioClassifier != null && length > 0) {
                // Reuse buffers
                short[] shortArray = getShortArray(length / 2);
                ByteBuffer buffer = getByteBuffer(length);
                
                buffer.clear();
                buffer.put(audioData, offset, length);
                buffer.rewind();
                buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray);
                
                List<TfLiteClassifier.Recognition> results = audioClassifier.recognize(shortArray);
                
                if (recordingStoreManager != null) {
                    for (TfLiteClassifier.Recognition result : results) {
                        if (result.getConfidence() > 0.3) {
                            recordingStoreManager.onTag(
                                new AudioTag(result.getTitle(), 
                                           result.getConfidence(), 
                                           System.currentTimeMillis())
                            );
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing audio", e);
        }
    }
    
    private short[] getShortArray(int size) {
        short[] array = shortArrayBuffer.get();
        if (array == null || array.length < size) {
            array = new short[size];
            shortArrayBuffer.set(array);
        }
        return array;
    }
    
    private ByteBuffer getByteBuffer(int size) {
        ByteBuffer buffer = byteBufferCache.get();
        if (buffer == null || buffer.capacity() < size) {
            buffer = ByteBuffer.allocate(size);
            byteBufferCache.set(buffer);
        }
        return buffer;
    }
    
    public synchronized void stop() {
        isRunning.set(false);
        
        // Clean up in reverse order of initialization
        if (audioClassifier != null) {
            try {
                audioClassifier.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing classifier", e);
            }
            audioClassifier = null;
        }
        
        if (segmentationController != null) {
            try {
                segmentationController.setListener(null); // Remove listener
                segmentationController.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing segmentation controller", e);
            }
            segmentationController = null;
        }
        
        if (recordingStoreManager != null) {
            try {
                recordingStoreManager.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing recording store", e);
            }
            recordingStoreManager = null;
        }
        
        if (vad != null) {
            try {
                vad.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing VAD", e);
            }
            vad = null;
        }
        
        // Clear thread local buffers
        shortArrayBuffer.remove();
        byteBufferCache.remove();
    }
    
    public RecordingStoreManager getRecordingStoreManager() {
        return recordingStoreManager;
    }
}
