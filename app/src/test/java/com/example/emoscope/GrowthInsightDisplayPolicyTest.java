package com.example.emoscope;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GrowthInsightDisplayPolicyTest {

    @Test
    public void hidesDetailedMetricsUntilThereAreEnoughRecords() {
        assertFalse(GrowthInsightDisplayPolicy.showsDetailedMetrics(0));
        assertFalse(GrowthInsightDisplayPolicy.showsDetailedMetrics(2));
    }

    @Test
    public void showsDetailedMetricsWhenTrendCanBeMeaningfullyRead() {
        assertTrue(GrowthInsightDisplayPolicy.showsDetailedMetrics(3));
    }

    @Test
    public void usesACompanionTitleBeforeThereIsEnoughDataForInsights() {
        assertEquals("慢慢看见自己", GrowthInsightDisplayPolicy.sectionTitle(0));
        assertEquals("今日洞察", GrowthInsightDisplayPolicy.sectionTitle(3));
        assertFalse(GrowthInsightDisplayPolicy.showsRefreshAction(2));
        assertTrue(GrowthInsightDisplayPolicy.showsRefreshAction(3));
    }
}
