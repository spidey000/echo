package eu.mrogalski.saidit.storage;

public class AudioTag {
    private final String label;
    private final float confidence;
    private final long timestamp;

    public AudioTag(String label, float confidence, long timestamp) {
        this.label = label;
        this.confidence = confidence;
        this.timestamp = timestamp;
    }

    public String getLabel() {
        return label;
    }

    public float getConfidence() {
        return confidence;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
