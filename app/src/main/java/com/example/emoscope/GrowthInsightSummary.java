package com.example.emoscope;

/** Creates cautious, observation-based copy for the growth screen. */
public final class GrowthInsightSummary {
    private static final int MINIMUM_RECORDS_FOR_TREND = 3;

    private GrowthInsightSummary() {
    }

    public static String build(int recordCount, int positiveRate) {
        if (recordCount <= 0) {
            return "从写下此刻开始。每一次诚实记录，都会成为认识自己的线索。";
        }
        if (recordCount < MINIMUM_RECORDS_FOR_TREND) {
            int remaining = MINIMUM_RECORDS_FOR_TREND - recordCount;
            return "你已经开始听见自己。再记录 " + remaining
                    + " 次，就能回看最初的情绪线索。";
        }
        return "最近 30 天记录了 " + recordCount + " 次，其中 " + positiveRate
                + "% 标记为相对平稳或积极。";
    }
}
