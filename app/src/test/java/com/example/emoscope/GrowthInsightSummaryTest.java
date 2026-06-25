package com.example.emoscope;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GrowthInsightSummaryTest {
    @Test
    public void welcomesTheFirstRecordWithoutTurningItIntoAnUpgradeTask() {
        assertEquals("从写下此刻开始。每一次诚实记录，都会成为认识自己的线索。",
                GrowthInsightSummary.build(0, 0));
    }

    @Test
    public void acknowledgesEarlyRecordsBeforeOfferingATrend() {
        assertEquals("你已经开始听见自己。再记录 2 次，就能回看最初的情绪线索。",
                GrowthInsightSummary.build(1, 0));
    }

    @Test
    public void summarizesOnlyObservedRecordCountsAndPositiveRate() {
        assertEquals("最近 30 天记录了 5 次，其中 80% 标记为相对平稳或积极。",
                GrowthInsightSummary.build(5, 80));
    }
}
