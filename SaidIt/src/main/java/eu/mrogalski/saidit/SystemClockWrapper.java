package eu.mrogalski.saidit;

import android.os.SystemClock;

public class SystemClockWrapper implements Clock {
    @Override
    public long uptimeMillis() {
        return SystemClock.uptimeMillis();
    }
}
