package com.example.emoscope.history;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;

import com.example.emoscope.AppBrand;
import com.example.emoscope.Constants;
import com.example.emoscope.EmoDatabaseHelper;
import com.example.emoscope.ResearchDataExporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Provides the non-UI parts of a history export: loading records, rendering content and saving it.
 */
public final class HistoryExportRepository {
    public static final int FORMAT_TEXT = 0;
    public static final int FORMAT_CSV = 1;
    public static final int FORMAT_MARKDOWN = 2;

    private HistoryExportRepository() {
    }

    public static List<HistoryExportFormatter.Record> loadRows(SQLiteDatabase database, int days) {
        List<HistoryExportFormatter.Record> rows = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("SELECT * FROM " + Constants.TABLE_RECORDS
                + " ORDER BY " + Constants.COL_ID + " DESC", null)) {
            while (cursor.moveToNext()) {
                String time = cursor.getString(1);
                if (days > 0 && !EmoDatabaseHelper.isWithinLastDays(time, days)) {
                    continue;
                }
                rows.add(new HistoryExportFormatter.Record(
                        time,
                        cursor.getString(2),
                        cursor.getString(3),
                        "1".equals(cursor.getString(4))));
            }
        }
        return rows;
    }

    public static ExportData buildExport(List<HistoryExportFormatter.Record> rows, int format,
                                         String timestamp, String generatedAt) {
        if (format == FORMAT_TEXT) {
            return new ExportData(AppBrand.reportFileName(timestamp, "txt"), "text/plain",
                    HistoryExportFormatter.buildText(rows, generatedAt));
        }
        if (format == FORMAT_CSV) {
            return new ExportData(AppBrand.reportFileName(timestamp, "csv"), "text/csv",
                    HistoryExportFormatter.buildCsv(rows));
        }
        if (format == FORMAT_MARKDOWN) {
            return new ExportData(AppBrand.reportFileName(timestamp, "md"), "text/markdown",
                    HistoryExportFormatter.buildMarkdown(rows, generatedAt));
        }
        throw new IllegalArgumentException("Unknown history export format: " + format);
    }

    public static List<ResearchDataExporter.Record> loadResearchRows(SQLiteDatabase database) {
        List<ResearchDataExporter.Record> rows = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("SELECT " + Constants.COL_TIME + ","
                + Constants.COL_TYPE + "," + Constants.COL_DETAIL + "," + Constants.COL_POSITIVE
                + " FROM " + Constants.TABLE_RECORDS + " ORDER BY " + Constants.COL_ID + " DESC", null)) {
            while (cursor.moveToNext()) {
                rows.add(new ResearchDataExporter.Record(cursor.getString(0), cursor.getString(1),
                        cursor.getString(2), cursor.getInt(3) == 1));
            }
        }
        return rows;
    }

    public static ExportData buildResearchExport(List<ResearchDataExporter.Record> rows,
                                                  boolean json, String timestamp) {
        String content = json
                ? ResearchDataExporter.buildAnonymousJson(rows, AppBrand.androidSourceLabel())
                : ResearchDataExporter.buildAnonymousCsv(rows);
        return new ExportData(AppBrand.researchFileName(timestamp, json),
                json ? "application/json" : "text/csv", content);
    }

    public static File writeExport(File directory, ExportData export) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create export directory: " + directory);
        }
        File file = new File(directory, export.fileName);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(export.content);
        }
        return file;
    }

    public static File selectImportDirectory(File downloadsDirectory) {
        File currentDirectory = new File(downloadsDirectory, AppBrand.EXPORT_DIRECTORY);
        if (currentDirectory.exists() && currentDirectory.listFiles() != null) {
            return currentDirectory;
        }
        File legacyDirectory = new File(downloadsDirectory, AppBrand.LEGACY_EXPORT_DIRECTORY);
        if (legacyDirectory.exists() && legacyDirectory.listFiles() != null) {
            return legacyDirectory;
        }
        return currentDirectory;
    }

    public static File findLatestJsonBackup(File directory) {
        File[] files = directory.listFiles((ignored, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return null;
        }
        File latest = files[0];
        for (File file : files) {
            if (file.lastModified() > latest.lastModified()) {
                latest = file;
            }
        }
        return latest;
    }

    public static String readUtf8(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                output.write(chunk, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static List<HistoryBackupFormatter.Record> loadBackupRows(SQLiteDatabase database) {
        List<HistoryBackupFormatter.Record> rows = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("SELECT * FROM " + Constants.TABLE_RECORDS
                + " ORDER BY " + Constants.COL_ID, null)) {
            while (cursor.moveToNext()) {
                rows.add(new HistoryBackupFormatter.Record(cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getInt(4)));
            }
        }
        return rows;
    }

    public static int importBackup(SQLiteDatabase database, String content) throws Exception {
        JSONArray records = new JSONArray(content);
        database.beginTransaction();
        try {
            for (int index = 0; index < records.length(); index++) {
                JSONObject record = records.getJSONObject(index);
                ContentValues values = new ContentValues();
                values.put(Constants.COL_TIME, record.getString("time"));
                values.put(Constants.COL_TYPE, record.getString("type"));
                values.put(Constants.COL_DETAIL, record.getString("detail"));
                values.put(Constants.COL_POSITIVE, record.optInt("positive", 1));
                database.insert(Constants.TABLE_RECORDS, null, values);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return records.length();
    }

    public static final class ExportData {
        public final String fileName;
        public final String mimeType;
        public final String content;

        public ExportData(String fileName, String mimeType, String content) {
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.content = content;
        }
    }
}
