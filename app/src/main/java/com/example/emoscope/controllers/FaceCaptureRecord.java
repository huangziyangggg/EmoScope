package com.example.emoscope.controllers;

/** Immutable, database-ready representation of a saved face-analysis snapshot. */
public final class FaceCaptureRecord {

    public final String type;
    public final String detail;
    public final boolean isPositive;
    private final int displayScore;

    private FaceCaptureRecord(String type, String detail, boolean isPositive, int displayScore) {
        this.type = type;
        this.detail = detail;
        this.isPositive = isPositive;
        this.displayScore = displayScore;
    }

    public static FaceCaptureRecord create(int rawWeightedScore, String probabilityOne,
                                           String probabilityTwo, String probabilityThree) {
        int boostedScore = FaceCaptureScorePolicy.displayScore(rawWeightedScore);
        String detail = "面容快照 | 情绪评分 " + boostedScore
                + " | ①" + probabilityOne + " ②" + probabilityTwo + " ③" + probabilityThree;
        return new FaceCaptureRecord("面容分析", detail, boostedScore >= 50, boostedScore);
    }

    public int displayScore() {
        return displayScore;
    }

    public String successMessage() {
        return "已保存 · 情绪评分 " + displayScore + "/100";
    }
}
