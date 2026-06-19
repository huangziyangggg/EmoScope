package com.example.emoscope;

public class SignalUpdateGate {
    private final long minIntervalMs;
    private final int minValueDelta;
    private boolean hasLastValue;
    private long lastUpdateMs;
    private int lastValue;

    public SignalUpdateGate(long minIntervalMs, int minValueDelta) {
        this.minIntervalMs = Math.max(0, minIntervalMs);
        this.minValueDelta = Math.max(0, minValueDelta);
    }

    public boolean shouldUpdate(long nowMs, int value) {
        if (!hasLastValue
                || nowMs - lastUpdateMs >= minIntervalMs
                || Math.abs(value - lastValue) >= minValueDelta) {
            hasLastValue = true;
            lastUpdateMs = nowMs;
            lastValue = value;
            return true;
        }
        return false;
    }
}
