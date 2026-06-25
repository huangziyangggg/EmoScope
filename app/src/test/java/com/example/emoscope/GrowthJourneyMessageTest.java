package com.example.emoscope;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GrowthJourneyMessageTest {

    @Test
    public void givesEmptyStateAHumanFirstStepInsteadOfLevelGamification() {
        GrowthJourneyMessage message = GrowthJourneyMessage.forRecords(0, 0,
                "情绪观察者", 5, 5);

        assertEquals("开始", message.badge);
        assertEquals("从一次记录开始", message.title);
        assertEquals("不是打卡任务，是留给自己的片刻空间", message.progress);
    }

    @Test
    public void keepsTheExistingLevelProgressForRecordedJourneys() {
        GrowthJourneyMessage message = GrowthJourneyMessage.forRecords(3, 0,
                "情绪观察者", 5, 2);

        assertEquals("Lv1", message.badge);
        assertEquals("情绪观察者", message.title);
        assertEquals("3/5 条记录 · 升级还需 2 条", message.progress);
    }

    @Test
    public void givesTheJournalEntryAShortReadableEmptyInvitation() {
        assertEquals("写下此刻", GrowthJourneyMessage.emptyJournalPreview());
    }
}
