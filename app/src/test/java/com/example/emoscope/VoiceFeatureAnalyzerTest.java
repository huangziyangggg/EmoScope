package com.example.emoscope;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VoiceFeatureAnalyzerTest {

    @Test
    public void detectsFastHighEnergySpeech() {
        VoiceFeatureAnalyzer.Result result =
                VoiceFeatureAnalyzer.analyze("今天事情太多我有点停不下来", 3.0f, 7.5f, 10.0f, 8);

        assertEquals(VoiceFeatureAnalyzer.Pace.FAST, result.pace);
        assertEquals(VoiceFeatureAnalyzer.Energy.HIGH, result.energy);
        assertTrue(result.summary.contains("偏快"));
        assertTrue(result.summary.contains("音量偏高"));
    }

    @Test
    public void detectsSlowLowEnergySpeech() {
        VoiceFeatureAnalyzer.Result result =
                VoiceFeatureAnalyzer.analyze("我 有点 累", 8.0f, 1.2f, 2.0f, 3);

        assertEquals(VoiceFeatureAnalyzer.Pace.SLOW, result.pace);
        assertEquals(VoiceFeatureAnalyzer.Energy.LOW, result.energy);
        assertTrue(result.summary.contains("偏慢"));
    }
}
