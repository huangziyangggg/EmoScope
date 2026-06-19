package com.example.emoscope;

public final class AppBrand {
    public static final String APP_NAME = "心镜";
    public static final String EXPORT_DIRECTORY = APP_NAME;
    public static final String LEGACY_EXPORT_DIRECTORY = "EmoScope";

    private AppBrand() {}

    public static String androidSourceLabel() {
        return APP_NAME + " Android";
    }

    public static String researchFileName(String timestamp, boolean json) {
        return APP_NAME + "_Research_" + timestamp + (json ? ".json" : ".csv");
    }

    public static String reportFileName(String timestamp, String extension) {
        return APP_NAME + "_" + timestamp + "." + extension;
    }

    public static String backupFileName(String timestamp) {
        return APP_NAME + "_Backup_" + timestamp + ".json";
    }

    public static String weeklyReportFileName(String timestamp) {
        return APP_NAME + "_Weekly_" + timestamp + ".png";
    }

    public static String localJsonExportFileName(String timestamp) {
        return "xinjing_export_" + timestamp + ".json";
    }
}
