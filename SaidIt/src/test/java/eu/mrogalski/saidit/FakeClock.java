package eu.mrogalski.saidit;

public class FakeClock implements Clock {
    private long currentTime = 0;

    @Override
    public long uptimeMillis() {
        return currentTime;
    }

    public void advance(long millis) {
        currentTime += millis;
    }
}
