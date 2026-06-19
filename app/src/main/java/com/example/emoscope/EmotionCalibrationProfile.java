package com.example.emoscope;

import java.util.Locale;

public class EmotionCalibrationProfile {
    public static final int NONE = -1;
    private static final float BOOST = 1.25f;

    private final int targetIndex;

    private EmotionCalibrationProfile(int targetIndex) {
        this.targetIndex = isValidIndex(targetIndex) ? targetIndex : NONE;
    }

    public static EmotionCalibrationProfile none() {
        return new EmotionCalibrationProfile(NONE);
    }

    public static EmotionCalibrationProfile fromTargetIndex(int targetIndex) {
        return new EmotionCalibrationProfile(targetIndex);
    }

    public static EmotionCalibrationProfile fromStorageString(String value) {
        if (value == null || value.trim().isEmpty()) return none();
        try {
            if (value.startsWith("target:")) {
                return fromTargetIndex(Integer.parseInt(value.substring("target:".length())));
            }
            return fromTargetIndex(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return none();
        }
    }

    public float[] apply(float[] rawPercentages) {
        if (rawPercentages == null) return new float[0];
        float[] adjusted = rawPercentages.clone();
        if (isValidIndex(targetIndex) && targetIndex < adjusted.length) {
            adjusted[targetIndex] *= BOOST;
        }
        normalize(adjusted);
        return adjusted;
    }

    public int getTargetIndex() {
        return targetIndex;
    }

    public boolean isEnabled() {
        return isValidIndex(targetIndex);
    }

    public String toStorageString() {
        return "target:" + targetIndex;
    }

    public String summary(String[] emotionNames) {
        if (!isEnabled()) return "未校准";
        String name = targetIndex < emotionNames.length ? emotionNames[targetIndex] : String.valueOf(targetIndex);
        return String.format(Locale.getDefault(), "已按“%s”校准", name);
    }

    private static boolean isValidIndex(int index) {
        return index >= 0 && index < 10;
    }

    private static void normalize(float[] values) {
        float sum = 0f;
        for (float value : values) {
            if (value > 0f) sum += value;
        }
        if (sum <= 0f) return;
        for (int i = 0; i < values.length; i++) {
            values[i] = Math.max(0f, values[i]) / sum * 100f;
        }
    }
}
