package com.example.emoscope;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResearchDataExporterTest {

    @Test
    public void exportJsonAnonymizesDetailAndIncludesMethodNote() {
        List<ResearchDataExporter.Record> records = Arrays.asList(
                new ResearchDataExporter.Record("2026-06-17 10:00", "自动分析", "原话: 今天很焦虑 电话13812345678", false),
                new ResearchDataExporter.Record("2026-06-17 11:00", "手动记录", "心情: 平静", true)
        );

        String json = ResearchDataExporter.buildAnonymousJson(records, "unit-test");

        assertTrue(json.contains("\"schema\":\"emoscope-research-export-v1\""));
        assertTrue(json.contains("\"source\":\"unit-test\""));
        assertTrue(json.contains("\"record_count\":2"));
        assertTrue(json.contains("[phone]"));
        assertFalse(json.contains("13812345678"));
    }
}
