package com.example.emoscope.controllers;

/** Immutable, database-ready representation of a saved face-analysis snapshot. */
public final class FaceCaptureRecord {

    public final String type;
    public final String detail;
    public final boolean isPositive;
    private final int weightedScore;

    private FaceCaptureRecord(String type, String detail, boolean isPositive, int weightedScore) {
        this.type = type;
        this.detail = detail;
        this.isPositive = isPositive;
        this.weightedScore = weightedScore;
    }

    public static FaceCaptureRecord create(int weightedScore, String probabilityOne,
                                           String probabilityTwo, String probabilityThree) {
        String detail = "面容快照 | 加权分: " + weightedScore
                + " | ①" + probabilityOne + " ②" + probabilityTwo + " ③" + probabilityThree;
        return new FaceCaptureRecord("面容分析", detail, weightedScore >= 50, weightedScore);
    }

    public String successMessage() {
        return "已保存 · 情绪分 " + weightedScore + "/100";
    }
}
