package com.example.emoscope;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OnboardingNarrativeTest {

    @Test
    public void introducesTheProductAsSupportForUnderstandingRatherThanScoringEmotion() {
        assertEquals("欢迎来到心镜", OnboardingNarrative.title());
        String message = OnboardingNarrative.message();
        assertTrue(message.contains("不是一张给情绪打分的表"));
        assertTrue(message.contains("安顿此刻"));
        assertTrue(message.contains("看见线索"));
        assertTrue(message.contains("走自己的路"));
        assertTrue(message.contains("不提供医疗诊断"));
    }

    @Test
    public void usesAQuietInvitationForThePrimaryAction() {
        assertEquals("从此刻开始", OnboardingNarrative.primaryActionLabel());
    }
}
