package com.example.emoscope;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MoodSelectionPolicyTest {

    @Test
    public void acceptsOnlyIndexesInsideTheMoodCatalog() {
        assertFalse(MoodSelectionPolicy.hasValidSelection(-1));
        assertTrue(MoodSelectionPolicy.hasValidSelection(0));
        assertTrue(MoodSelectionPolicy.hasValidSelection(Constants.MANUAL_MOOD_LABELS.length - 1));
        assertFalse(MoodSelectionPolicy.hasValidSelection(Constants.MANUAL_MOOD_LABELS.length));
    }

    @Test
    public void marksCalmAndPositiveMoodsAsPositive() {
        assertTrue(MoodSelectionPolicy.isPositiveMood(0));
        assertTrue(MoodSelectionPolicy.isPositiveMood(2));
        assertFalse(MoodSelectionPolicy.isPositiveMood(3));
        assertFalse(MoodSelectionPolicy.isPositiveMood(-1));
    }
}
