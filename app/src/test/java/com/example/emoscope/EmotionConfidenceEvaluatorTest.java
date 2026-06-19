package com.example.emoscope;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EmotionConfidenceEvaluatorTest {

    @Test
    public void lowLightAndCloseScoresProduceCaution() {
        float[] percentages = new float[]{24f, 22f, 20f, 10f, 8f, 6f, 4f, 3f, 2f, 1f};

        EmotionConfidenceEvaluator.Result result =
                EmotionConfidenceEvaluator.evaluate(percentages, 45, true);

        assertFalse(result.isReliable);
        assertTrue(result.message.contains("光线"));
        assertTrue(result.message.contains("接近"));
    }

    @Test
    public void clearSignalInNormalLightIsReliable() {
        float[] percentages = new float[]{70f, 10f, 6f, 4f, 3f, 2f, 2f, 1f, 1f, 1f};

        EmotionConfidenceEvaluator.Result result =
                EmotionConfidenceEvaluator.evaluate(percentages, 120, true);

        assertTrue(result.isReliable);
        assertTrue(result.message.contains("较稳定"));
    }
}
