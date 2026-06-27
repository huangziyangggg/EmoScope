package com.example.emoscope.controllers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FaceCaptureScorePolicyTest {

    @Test
    public void multipliesCaptureScoresByOnePointSix() {
        assertEquals(80, FaceCaptureScorePolicy.displayScore(50));
    }

    @Test
    public void capsCaptureScoresAtOneHundred() {
        assertEquals(100, FaceCaptureScorePolicy.displayScore(90));
    }

    @Test
    public void clampsUnexpectedNegativeScores() {
        assertEquals(0, FaceCaptureScorePolicy.displayScore(-10));
    }
}
