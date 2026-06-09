package com.example.emoscope;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 接收 AlarmManager 定时广播，触发对应通知。
 * 支持每日提醒 (DAILY_REMINDER) 和每周报告 (WEEKLY_SUMMARY)。
 */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        Log.i(Constants.TAG, "ReminderReceiver: received " + action);

        if ("com.example.emoscope.DAILY_REMINDER".equals(action)) {
            NotificationHelper.showDailyReminder(context);
            // 重新调度下一次提醒
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    Constants.PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getBoolean(Constants.KEY_NOTIFY_DAILY, false)) {
                int hour = prefs.getInt(Constants.KEY_NOTIFY_HOUR, 20);
                int minute = prefs.getInt(Constants.KEY_NOTIFY_MINUTE, 0);
                NotificationHelper.scheduleDailyReminder(context, hour, minute);
            }
        } else if ("com.example.emoscope.WEEKLY_SUMMARY".equals(action)) {
            // 查询本周统计
            android.database.sqlite.SQLiteDatabase db = new EmoDatabaseHelper(context).getReadableDatabase();
            android.database.Cursor cursor = null;
            int total = 0, pos = 0, neg = 0;
            try {
                // 本周一 00:00 起
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                java.util.Date weekStart = cal.getTime();

                cursor = db.rawQuery(
                        "SELECT " + Constants.COL_TIME + ", " + Constants.COL_POSITIVE
                                + " FROM " + Constants.TABLE_RECORDS
                                + " ORDER BY " + Constants.COL_ID + " DESC LIMIT 200",
                        null);
                while (cursor.moveToNext()) {
                    if (!EmoDatabaseHelper.isAtOrAfter(cursor.getString(0), weekStart)) continue;
                    if (cursor.getInt(1) == 1) pos++;
                    else neg++;
                }
                total = pos + neg;
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
            NotificationHelper.showWeeklySummary(context, total, pos, neg);

            // 重新调度下周报告
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    Constants.PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getBoolean(Constants.KEY_NOTIFY_WEEKLY, false)) {
                NotificationHelper.scheduleWeeklySummary(context);
            }
        }
    }
}
