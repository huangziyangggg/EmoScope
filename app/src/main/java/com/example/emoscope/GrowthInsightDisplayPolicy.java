package com.example.emoscope;

/** Controls when the growth screen can show record-derived detailed metrics. */
public final class GrowthInsightDisplayPolicy {

    private static final int MINIMUM_RECORDS_FOR_DETAILED_METRICS = 3;

    private GrowthInsightDisplayPolicy() {
    }

    public static boolean showsDetailedMetrics(int recordCount) {
        return recordCount >= MINIMUM_RECORDS_FOR_DETAILED_METRICS;
    }

    public static boolean showsRefreshAction(int recordCount) {
        return showsDetailedMetrics(recordCount);
    }

    public static String sectionTitle(int recordCount) {
        return recordCount <= 0 ? "慢慢看见自己" : "今日洞察";
    }
}
