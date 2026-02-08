package eu.mrogalski.saidit.analysis;

public interface SegmentationController {

    interface SegmentListener {
        void onSegmentStart(long timestamp);
        void onSegmentEnd(long timestamp);
        void onSegmentData(byte[] data, int offset, int length);
    }

    void process(byte[] pcm, int offset, int length, boolean isSpeech);

    void setListener(SegmentListener listener);

    void close();
}
