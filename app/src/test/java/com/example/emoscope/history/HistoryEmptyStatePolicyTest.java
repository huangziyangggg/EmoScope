package com.example.emoscope.history;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HistoryEmptyStatePolicyTest {

    @Test
    public void recordActionIsShownOnlyWhenHistoryIsEmpty() {
        assertTrue(HistoryEmptyStatePolicy.shouldShowRecordAction(true));
        assertFalse(HistoryEmptyStatePolicy.shouldShowRecordAction(false));
    }
}
