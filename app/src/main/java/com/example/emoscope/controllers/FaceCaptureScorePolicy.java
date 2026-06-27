package com.example.emoscope.controllers;

/** Scales camera snapshot scores for user-facing face capture records. */
public final class FaceCaptureScorePolicy {

    static final float CAPTURE_SCORE_MULTIPLIER = 1.6f;

    private FaceCaptureScorePolicy() {
    }

    public static int displayScore(int rawWeightedScore) {
        int safeRawScore = Math.max(0, Math.min(100, rawWeightedScore));
        return Math.max(0, Math.min(100, Math.round(safeRawScore * CAPTURE_SCORE_MULTIPLIER)));
    }
}
