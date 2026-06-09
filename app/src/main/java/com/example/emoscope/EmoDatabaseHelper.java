package com.example.emoscope;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * EmoScope 数据库助手 — 管理情绪记录表的创建、版本迁移与统一写入。
 * 从 MainActivity 内部类提取为独立类，供 MainActivity、ReminderReceiver 和 Fragment 共用。
 */
public class EmoDatabaseHelper extends SQLiteOpenHelper {

    private static final SimpleDateFormat SDF_DB = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private static final SimpleDateFormat SDF_DAY = new SimpleDateFormat("MM-dd", Locale.getDefault());
    private static final SimpleDateFormat SDF_LEGACY_DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat SDF_LEGACY_DB = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public EmoDatabaseHelper(Context ctx) {
        super(ctx, Constants.DB_NAME, null, Constants.DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + Constants.TABLE_RECORDS + " ("
                + Constants.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Constants.COL_TIME + " TEXT, "
                + Constants.COL_TYPE + " TEXT, "
                + Constants.COL_DETAIL + " TEXT, "
                + Constants.COL_POSITIVE + " INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + Constants.TABLE_RECORDS);
        onCreate(db);
    }

    /**
     * 统一写入记录方法。所有 Fragment 和 Activity 共用此入口。
     * 线程安全：调用方应在后台线程执行。
     */
    public void saveRecord(String type, String detail, boolean positive) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        synchronized (SDF_DB) {
            values.put(Constants.COL_TIME, SDF_DB.format(new Date()));
        }
        values.put(Constants.COL_TYPE, type);
        values.put(Constants.COL_DETAIL, detail);
        values.put(Constants.COL_POSITIVE, positive ? 1 : 0);
        db.insert(Constants.TABLE_RECORDS, null, values);
        db.close();
    }

    public static String dayPrefix(Date date) {
        synchronized (SDF_DAY) {
            return SDF_DAY.format(date);
        }
    }

    public static String legacyDayPrefix(Date date) {
        synchronized (SDF_LEGACY_DAY) {
            return SDF_LEGACY_DAY.format(date);
        }
    }

    public static String[] dayLikeArgs(Date date) {
        return new String[]{dayPrefix(date) + "%", legacyDayPrefix(date) + "%"};
    }

    public static boolean isWithinLastDays(String storedTime, int days) {
        Date parsed = parseStoredTime(storedTime);
        if (parsed == null) return false;
        Calendar min = Calendar.getInstance();
        min.add(Calendar.DAY_OF_YEAR, -days);
        return !parsed.before(min.getTime());
    }

    public static boolean isAtOrAfter(String storedTime, Date start) {
        Date parsed = parseStoredTime(storedTime);
        return parsed != null && !parsed.before(start);
    }

    public static boolean isSameDay(String storedTime, Date date) {
        if (storedTime == null) return false;
        return storedTime.startsWith(dayPrefix(date))
                || storedTime.startsWith(legacyDayPrefix(date));
    }

    private static Date parseStoredTime(String storedTime) {
        if (storedTime == null || storedTime.trim().isEmpty()) return null;
        synchronized (SDF_LEGACY_DB) {
            try {
                if (storedTime.length() >= 16 && storedTime.charAt(4) == '-') {
                    return SDF_LEGACY_DB.parse(storedTime);
                }
            } catch (ParseException ignored) {
            }
        }
        synchronized (SDF_DB) {
            try {
                Date date = SDF_DB.parse(storedTime);
                if (date == null) return null;
                Calendar parsed = Calendar.getInstance();
                Calendar now = Calendar.getInstance();
                parsed.setTime(date);
                parsed.set(Calendar.YEAR, now.get(Calendar.YEAR));
                if (parsed.after(now)) parsed.add(Calendar.YEAR, -1);
                return parsed.getTime();
            } catch (ParseException ignored) {
                return null;
            }
        }
    }

    /** Delete all locally stored emotion records. */
    public void clearAllRecords() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(Constants.TABLE_RECORDS, null, null);
        db.close();
    }
}
