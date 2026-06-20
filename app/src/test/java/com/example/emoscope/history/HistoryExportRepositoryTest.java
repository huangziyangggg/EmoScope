package com.example.emoscope.history;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import com.example.emoscope.ResearchDataExporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HistoryExportRepositoryTest {
    @Test
    public void createsCsvExportWithBrandFileNameAndMimeType() {
        HistoryExportFormatter.Record record = new HistoryExportFormatter.Record(
                "2026-06-20 09:00", "manual", "calm", true);

        HistoryExportRepository.ExportData export = HistoryExportRepository.buildExport(
                Collections.singletonList(record),
                HistoryExportRepository.FORMAT_CSV,
                "20260620_090000",
                "2026-06-20 09:00");

        assertEquals("心镜_20260620_090000.csv", export.fileName);
        assertEquals("text/csv", export.mimeType);
        assertTrue(export.content.startsWith("\uFEFF"));
    }

    @Test
    public void writesExportContentAsUtf8() throws Exception {
        HistoryExportRepository.ExportData export = new HistoryExportRepository.ExportData(
                "心镜_20260620_090000.txt", "text/plain", "心镜记录");
        File directory = Files.createTempDirectory("xinjing-export").toFile();

        File output = HistoryExportRepository.writeExport(directory, export);

        assertEquals(export.fileName, output.getName());
        assertEquals("心镜记录", new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void createsAnonymousResearchJsonWithBrandFileNameAndMimeType() {
        ResearchDataExporter.Record record = new ResearchDataExporter.Record(
                "2026-06-20 09:00", "manual", "call 13812345678", true);

        HistoryExportRepository.ExportData export = HistoryExportRepository.buildResearchExport(
                Collections.singletonList(record), true, "20260620_090000");

        assertEquals("心镜_Research_20260620_090000.json", export.fileName);
        assertEquals("application/json", export.mimeType);
        assertTrue(export.content.contains("[phone]"));
    }

    @Test
    public void usesLegacyDirectoryWhenCurrentExportDirectoryIsUnavailable() throws Exception {
        File downloads = Files.createTempDirectory("xinjing-downloads").toFile();
        File legacy = new File(downloads, "EmoScope");
        assertTrue(legacy.mkdirs());

        File directory = HistoryExportRepository.selectImportDirectory(downloads);

        assertEquals(legacy, directory);
    }
}
