package eu.mrogalski.saidit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AudioMemory {
    static final int CHUNK_SIZE = 1920000;
    
    private final Clock clock;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    // Ring buffer
    private ByteBuffer ring;
    private int capacity = 0;
    private int writePos = 0;
    private int size = 0;
    
    // Fill estimation
    private volatile long fillingStartUptimeMillis;
    private volatile boolean filling = false;
    private volatile boolean overwriting = false;
    
    // Thread-local IO buffer to avoid allocations
    private final ThreadLocal<byte[]> ioBuffer = ThreadLocal.withInitial(() -> new byte[32 * 1024]);
    
    public AudioMemory(Clock clock) {
        this.clock = clock;
    }
    
    public interface Consumer {
        int consume(byte[] array, int offset, int count) throws IOException;
    }
    
    public void allocate(long sizeToEnsure) {
        rwLock.writeLock().lock();
        try {
            int required = 0;
            while (required < sizeToEnsure) required += CHUNK_SIZE;
            if (required == capacity) return;
            
            // Clear old buffer first to help GC
            if (ring != null) {
                ring.clear();
                ring = null;
                System.gc(); // Hint to GC
            }
            
            ring = (required > 0) ? ByteBuffer.allocateDirect(required) : null;
            capacity = required;
            writePos = 0;
            size = 0;
            overwriting = false;
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    public int fill(Consumer filler) throws IOException {
        int totalRead = 0;
        int read;
        
        // Set filling flag
        rwLock.readLock().lock();
        try {
            if (capacity == 0 || ring == null) return 0;
            filling = true;
            fillingStartUptimeMillis = clock.uptimeMillis();
        } finally {
            rwLock.readLock().unlock();
        }
        
        byte[] buffer = ioBuffer.get();
        
        while ((read = filler.consume(buffer, 0, buffer.length)) > 0) {
            rwLock.writeLock().lock();
            try {
                if (capacity == 0 || ring == null) break;
                
                // Write into ring with wrap-around
                int first = Math.min(read, capacity - writePos);
                if (first > 0) {
                    ring.position(writePos);
                    ring.put(buffer, 0, first);
                }
                
                int remaining = read - first;
                if (remaining > 0) {
                    ring.position(0);
                    ring.put(buffer, first, remaining);
                }
                
                writePos = (writePos + read) % capacity;
                int newSize = size + read;
                if (newSize > capacity) {
                    overwriting = true;
                    size = capacity;
                } else {
                    size = newSize;
                }
                totalRead += read;
            } finally {
                rwLock.writeLock().unlock();
            }
        }
        
        // Clear filling flag
        rwLock.readLock().lock();
        try {
            filling = false;
        } finally {
            rwLock.readLock().unlock();
        }
        
        return totalRead;
    }
    
    public void dump(Consumer consumer, int bytesToDump) throws IOException {
        rwLock.readLock().lock();
        try {
            if (capacity == 0 || ring == null || size == 0 || bytesToDump <= 0) return;
            
            int toCopy = Math.min(bytesToDump, size);
            int skip = size - toCopy;
            
            int start = (writePos - size + capacity) % capacity;
            int readPos = (start + skip) % capacity;
            
            byte[] buffer = ioBuffer.get();
            int remaining = toCopy;
            
            while (remaining > 0) {
                int chunk = Math.min(Math.min(remaining, capacity - readPos), buffer.length);
                ring.position(readPos);
                ring.get(buffer, 0, chunk);
                consumer.consume(buffer, 0, chunk);
                remaining -= chunk;
                readPos = (readPos + chunk) % capacity;
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void read(int startOffset, int bytesToRead, Consumer consumer) throws IOException {
        rwLock.readLock().lock();
        try {
            if (capacity == 0 || ring == null || size == 0 || bytesToRead <= 0) return;
            if (startOffset >= size) return;
            if (startOffset + bytesToRead > size) {
                bytesToRead = size - startOffset;
            }
            int bufferStartPos = (writePos - size + capacity) % capacity;
            int readPos = (bufferStartPos + startOffset) % capacity;
            byte[] buffer = ioBuffer.get();
            int remaining = bytesToRead;
            while (remaining > 0) {
                int toReadFromRing = Math.min(remaining, capacity - readPos);
                int chunk = Math.min(toReadFromRing, buffer.length);
                ring.position(readPos);
                ring.get(buffer, 0, chunk);
                consumer.consume(buffer, 0, chunk);
                remaining -= chunk;
                readPos = (readPos + chunk) % capacity;
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public long getAllocatedMemorySize() {
        rwLock.readLock().lock();
        try {
            return capacity;
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    public Stats getStats(int fillRate) {
        rwLock.readLock().lock();
        try {
            final Stats stats = new Stats();
            stats.filled = size;
            stats.total = capacity;
            stats.writePos = writePos;
            stats.estimation = (int) (filling ? 
                (clock.uptimeMillis() - fillingStartUptimeMillis) * fillRate / 1000 : 0);
            stats.overwriting = overwriting;
            return stats;
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    public static class Stats {
        public int filled;
        public int total;
        public int writePos;
        public int estimation;
        public boolean overwriting;
    }
}
