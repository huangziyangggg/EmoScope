package com.example.emoscope;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Centralizes destructive local-data operations and data export so privacy
 * controls and data portability do not depend on UI implementation details.
 */
public final class LocalDataManager {

    private LocalDataManager() {}

    public static void clearAllLocalData(Context context) {
        NotificationHelper.cancelDailyReminder(context);
        NotificationHelper.cancelWeeklySummary(context);

        new EmoDatabaseHelper(context).clearAllRecords();
        new SecureStorage(context).clear();

        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    /**
     * Export all emotion records as a JSON file to external storage.
     * @return the File written, or null on failure.
     */
    public static File exportRecordsJson(Context context) {
        EmoDatabaseHelper helper = new EmoDatabaseHelper(context);
        SQLiteDatabase db = null;
        Cursor cursor = null;
        FileWriter writer = null;
        try {
            db = helper.getReadableDatabase();
            cursor = db.rawQuery("SELECT " + Constants.COL_TIME + ", "
                    + Constants.COL_TYPE + ", " + Constants.COL_DETAIL + ", "
                    + Constants.COL_POSITIVE + " FROM " + Constants.TABLE_RECORDS
                    + " ORDER BY " + Constants.COL_ID + " ASC", null);

            JSONArray array = new JSONArray();
            while (cursor.moveToNext()) {
                JSONObject obj = new JSONObject();
                obj.put("time", cursor.getString(0));
                obj.put("type", cursor.getString(1));
                obj.put("detail", cursor.getString(2));
                obj.put("positive", cursor.getInt(3) == 1);
                array.put(obj);
            }

            JSONObject root = new JSONObject();
            root.put("exportDate", new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date()));
            root.put("totalRecords", array.length());
            root.put("records", array);

            File dir = context.getExternalFilesDir("exports");
            if (dir != null && !dir.exists()) dir.mkdirs();
            String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                    java.util.Locale.getDefault()).format(new java.util.Date());
            File outFile = new File(dir, "emoscope_export_" + ts + ".json");
            writer = new FileWriter(outFile);
            writer.write(root.toString(2));
            return outFile;

        } catch (Exception e) {
            Log.e(Constants.TAG, "exportRecordsJson failed", e);
            return null;
        } finally {
            if (cursor != null) cursor.close();
            if (writer != null) { try { writer.close(); } catch (Exception ignored) {} }
        }
    }
}
