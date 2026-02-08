package eu.mrogalski.saidit;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioMemory {

    // Keep chunk size as allocation granularity (20s @ 48kHz mono 16-bit)
    static final int CHUNK_SIZE = 1920000; // bytes

    private final Clock clock;

    // Ring buffer
    private ByteBuffer ring; // direct buffer
    private int capacity = 0; // bytes
    private int writePos = 0; // next write index [0..capacity)
    private int size = 0;     // number of valid bytes stored (<= capacity)

    // Fill estimation
    private long fillingStartUptimeMillis;
    private boolean filling = false;
    private boolean overwriting = false;

    // Reusable IO buffer to reduce allocations when interacting with AudioRecord/consumers
    private byte[] ioBuffer = new byte[32 * 1024];

    public AudioMemory(Clock clock) {
        this.clock = clock;
    }

    public interface Consumer {
        int consume(byte[] array, int offset, int count) throws IOException;
    }

    synchronized public void allocate(long sizeToEnsure) {
        int required = 0;
        while (required < sizeToEnsure) required += CHUNK_SIZE;
        if (required == capacity) return; // no change

        // Allocate new ring; drop previous content to free memory pressure.
        ring = (required > 0) ? ByteBuffer.allocateDirect(required) : null;
        capacity = required;
        writePos = 0;
        size = 0;
        overwriting = false;
    }

    synchronized public long getAllocatedMemorySize() {
        return capacity;
    }

    public int countFilled() {
        synchronized (this) {
            return size;
        }
    }

    // Ensure ioBuffer is at least min bytes
    private void ensureIoBuffer(int min) {
        if (ioBuffer.length < min) {
            int newLen = ioBuffer.length;
            while (newLen < min) newLen = Math.min(Math.max(newLen * 2, 4096), 256 * 1024);
            ioBuffer = new byte[newLen];
        }
    }

    // Fill ring buffer with newly recorded data. Returns number of bytes read from the consumer.
    public int fill(Consumer filler) throws IOException {
        int totalRead = 0;
        int read;
        synchronized (this) {
            if (capacity == 0 || ring == null) return 0;
            filling = true;
            fillingStartUptimeMillis = clock.uptimeMillis();
        }

        ensureIoBuffer(32 * 1024);

        // The filler might provide data in multiple chunks.
        while ((read = filler.consume(ioBuffer, 0, ioBuffer.length)) > 0) {
            synchronized (this) {
                if (read > 0 && capacity > 0) { // check capacity again inside sync block
                    // Write into ring with wrap-around
                    int first = Math.min(read, capacity - writePos);
                    if (first > 0) {
                        ByteBuffer dup = ring.duplicate();
                        dup.position(writePos);
                        dup.put(ioBuffer, 0, first);
                    }
                    int remaining = read - first;
                    if (remaining > 0) {
                        ByteBuffer dup = ring.duplicate();
                        dup.position(0);
                        dup.put(ioBuffer, first, remaining);
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
                } else {
                    // capacity became 0, stop filling
                    break;
                }
            }
        }

        synchronized (this) {
            filling = false;
        }
        return totalRead;
    }


    public synchronized void dump(Consumer consumer, int bytesToDump) throws IOException {
        if (capacity == 0 || ring == null || size == 0 || bytesToDump <= 0) return;

        int toCopy = Math.min(bytesToDump, size);
        int skip = size - toCopy; // skip older bytes beyond window

        int start = (writePos - size + capacity) % capacity; // oldest
        int readPos = (start + skip) % capacity;             // first byte to output

        int remaining = toCopy;
        while (remaining > 0) {
            int chunk = Math.min(remaining, capacity - readPos);
            // Copy out chunk into consumer via temporary array
            ensureIoBuffer(chunk);
            ByteBuffer dup = ring.duplicate();
            dup.position(readPos);
            dup.get(ioBuffer, 0, chunk);
            consumer.consume(ioBuffer, 0, chunk);
            remaining -= chunk;
            readPos = (readPos + chunk) % capacity;
        }
    }

    public static class Stats {
        public int filled; // bytes stored
        public int total;  // capacity
        public int estimation; // bytes assumed in flight since last fill started
        public boolean overwriting; // whether we've wrapped at least once
    }

    public synchronized Stats getStats(int fillRate) {
        final Stats stats = new Stats();
        stats.filled = size;
        stats.total = capacity;
        stats.estimation = (int) (filling ? (clock.uptimeMillis() - fillingStartUptimeMillis) * fillRate / 1000 : 0);
        stats.overwriting = overwriting;
        return stats;
    }
}
