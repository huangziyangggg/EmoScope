package com.example.emoscope;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EmotionCalibrationProfileTest {

    @Test
    public void calibrationBoostsSelectedEmotionAndKeepsTotalNearHundred() {
        float[] raw = new float[]{20f, 30f, 10f, 5f, 8f, 8f, 7f, 4f, 3f, 5f};
        EmotionCalibrationProfile profile = EmotionCalibrationProfile.fromTargetIndex(0);

        float[] adjusted = profile.apply(raw);

        assertTrue(adjusted[0] > raw[0]);
        float total = 0f;
        for (float value : adjusted) total += value;
        assertEquals(100f, total, 0.2f);
    }

    @Test
    public void profileRoundTripsThroughStorageString() {
        EmotionCalibrationProfile profile = EmotionCalibrationProfile.fromTargetIndex(5);

        EmotionCalibrationProfile parsed = EmotionCalibrationProfile.fromStorageString(profile.toStorageString());

        assertEquals(5, parsed.getTargetIndex());
        assertEquals(profile.toStorageString(), parsed.toStorageString());
    }
}
