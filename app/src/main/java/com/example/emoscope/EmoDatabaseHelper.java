package com.example.emoscope;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * EmoScope 数据库助手 — 管理情绪记录表的创建、版本迁移与统一写入。
 * 从 MainActivity 内部类提取为独立类，供 MainActivity、ReminderReceiver 和 Fragment 共用。
 */
public class EmoDatabaseHelper extends SQLiteOpenHelper {

    private static final SimpleDateFormat SDF_DB = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    EmoDatabaseHelper(Context ctx) {
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
}
