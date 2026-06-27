package com.example.emoscope;

/** Supplies the level-card copy for a user's current growth stage. */
public final class GrowthJourneyMessage {

    public final String badge;
    public final String title;
    public final String progress;

    private GrowthJourneyMessage(String badge, String title, String progress) {
        this.badge = badge;
        this.title = title;
        this.progress = progress;
    }

    public static GrowthJourneyMessage forRecords(int recordCount, int level,
                                                   String levelName, int nextThreshold,
                                                   int recordsNeeded) {
        if (recordCount <= 0) {
            return new GrowthJourneyMessage("开始", "从一次记录开始",
                    "不是打卡任务，是留给自己的片刻空间");
        }

        String progress = recordsNeeded > 0
                ? recordCount + "/" + nextThreshold + " 条记录 · 升级还需 " + recordsNeeded + " 条"
                : recordCount + " 条记录 · 已达最高等级";
        return new GrowthJourneyMessage("Lv" + (level + 1), levelName, progress);
    }

    public static String emptyJournalPreview() {
        return "写下此刻";
    }
}
