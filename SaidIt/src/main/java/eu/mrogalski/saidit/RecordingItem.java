package eu.mrogalski.saidit;

import android.net.Uri;

public class RecordingItem {
    private final Uri uri;
    private final String name;
    private final long date;
    private final long duration;

    public RecordingItem(Uri uri, String name, long date, long duration) {
        this.uri = uri;
        this.name = name;
        this.date = date;
        this.duration = duration;
    }

    public Uri getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public long getDate() {
        return date;
    }

    public long getDuration() {
        return duration;
    }
}
