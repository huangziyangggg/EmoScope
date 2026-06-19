package com.example.emoscope;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppBrandTest {
    @Test
    public void exposesChineseProductNameAndCompatibleDirectories() {
        assertEquals("心镜", AppBrand.APP_NAME);
        assertEquals("心镜", AppBrand.EXPORT_DIRECTORY);
        assertEquals("EmoScope", AppBrand.LEGACY_EXPORT_DIRECTORY);
        assertEquals("心镜 Android", AppBrand.androidSourceLabel());
    }

    @Test
    public void buildsUserVisibleExportFileNames() {
        assertEquals("心镜_Research_20260619_140000.json",
                AppBrand.researchFileName("20260619_140000", true));
        assertEquals("心镜_Research_20260619_140000.csv",
                AppBrand.researchFileName("20260619_140000", false));
        assertEquals("心镜_20260619_140000.md",
                AppBrand.reportFileName("20260619_140000", "md"));
        assertEquals("心镜_Backup_20260619_140000.json",
                AppBrand.backupFileName("20260619_140000"));
        assertEquals("心镜_Weekly_20260619_140000.png",
                AppBrand.weeklyReportFileName("20260619_140000"));
        assertEquals("xinjing_export_20260619_140000.json",
                AppBrand.localJsonExportFileName("20260619_140000"));
    }
}
