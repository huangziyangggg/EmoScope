package com.example.emoscope;

/**
 * 心情选择的无界面规则，供所有记录入口复用。
 */
public final class MoodSelectionPolicy {

    private static final int POSITIVE_MOOD_LAST_INDEX = 2;

    private MoodSelectionPolicy() {
    }

    public static boolean hasValidSelection(int index) {
        return index >= 0 && index < Constants.MANUAL_MOOD_LABELS.length;
    }

    public static boolean isPositiveMood(int index) {
        return hasValidSelection(index) && index <= POSITIVE_MOOD_LAST_INDEX;
    }
}
