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

    @Test
    public void gentleDescriptionIsNonDiagnosticAndWarm() {
        VoiceFeatureAnalyzer.Result fastResult =
                VoiceFeatureAnalyzer.analyze("今天事情很多", 2.0f, 8.0f, 10.0f, 8);
        String fastHint = VoiceFeatureAnalyzer.gentleDescription(fastResult);
        assertTrue(fastHint.contains("快"));
        assertTrue(fastHint.contains("响亮"));

        VoiceFeatureAnalyzer.Result slowResult =
                VoiceFeatureAnalyzer.analyze("我有点累", 8.0f, 1.0f, 2.0f, 3);
        String slowHint = VoiceFeatureAnalyzer.gentleDescription(slowResult);
        assertTrue(slowHint.contains("慢"));
        assertTrue(slowHint.contains("轻声"));

        // 验证包含非诊断提醒
        assertTrue(fastHint.contains("提醒"));
        assertTrue(slowHint.contains("提醒"));
    }

    @Test
    public void gentleDescriptionHandlesNullGracefully() {
        assertEquals("暂时没有语音数据", VoiceFeatureAnalyzer.gentleDescription(null));
    }
}
