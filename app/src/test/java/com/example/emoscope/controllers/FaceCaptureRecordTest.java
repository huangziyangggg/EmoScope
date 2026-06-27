package com.example.emoscope.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FaceCaptureRecordTest {

    @Test
    public void buildsFaceSnapshotRecordWithOnlyTheFinalBoostedScore() {
        FaceCaptureRecord record = FaceCaptureRecord.create(
                50, "开心 60%", "平静 25%", "惊讶 15%");

        assertEquals("面容分析", record.type);
        assertEquals("面容快照 | 情绪评分 80 | ①开心 60% ②平静 25% ③惊讶 15%",
                record.detail);
        assertTrue(record.isPositive);
        assertEquals("已保存 · 情绪评分 80/100", record.successMessage());
    }

    @Test
    public void marksBoostedScoresBelowTheThresholdAsWarningRecords() {
        FaceCaptureRecord record = FaceCaptureRecord.create(
                25, "难过 60%", "平静 25%", "疲惫 15%");

        assertFalse(record.isPositive);
    }
}
