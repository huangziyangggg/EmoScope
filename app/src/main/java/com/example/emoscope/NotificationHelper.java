package com.example.emoscope;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;

/**
 * 通知管理助手 — 负责创建通知渠道、显示通知、调度定时提醒。
 * 支持 Android 8.0+ 通知渠道和 Android 13+ 运行时通知权限。
 */
public final class NotificationHelper {

    public static final String CHANNEL_DAILY = "daily_reminder";
    public static final String CHANNEL_WEEKLY = "weekly_summary";

    private static final int NOTIFY_DAILY_ID = 2001;
    private static final int NOTIFY_WEEKLY_ID = 2002;
    private static final int REQUEST_DAILY = 3001;
    private static final int REQUEST_WEEKLY = 3002;

    private NotificationHelper() {}

    /** 初始化通知渠道 — 在 Application 或 onCreate 中调用 */
    public static void createChannels(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // 每日提醒渠道
        NotificationChannel dailyChannel = new NotificationChannel(
                CHANNEL_DAILY,
                "每日心情提醒",
                NotificationManager.IMPORTANCE_DEFAULT);
        dailyChannel.setDescription("每天定时提醒你记录情绪状态");
        dailyChannel.enableVibration(true);
        nm.createNotificationChannel(dailyChannel);

        // 每周报告渠道
        NotificationChannel weeklyChannel = new NotificationChannel(
                CHANNEL_WEEKLY,
                "每周情绪报告",
                NotificationManager.IMPORTANCE_DEFAULT);
        weeklyChannel.setDescription("每周日推送情绪趋势总结");
        weeklyChannel.enableVibration(true);
        nm.createNotificationChannel(weeklyChannel);
    }

    /** 发送每日心情打卡提醒 */
    public static void showDailyReminder(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // C3: 根据近期情绪数据定制推送文案
        String customMessage = buildCustomMessage(context);
        String title = "今天的心情如何？";
        String summary = title + "\n\n点击记录今日情绪 →";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_DAILY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(customMessage)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(summary))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(createOpenAppIntent(context));

        nm.notify(NOTIFY_DAILY_ID, builder.build());
    }

    /** C3: 根据用户近期情绪数据生成定制提醒文案 */
    private static String buildCustomMessage(Context context) {
        try {
            android.database.sqlite.SQLiteDatabase db = new EmoDatabaseHelper(context).getReadableDatabase();
            android.database.Cursor cursor = null;
            try {
                // 查询昨天的情绪记录
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
                String yesterday = new java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                        .format(cal.getTime());
                cursor = db.rawQuery(
                        "SELECT " + Constants.COL_POSITIVE + " FROM " + Constants.TABLE_RECORDS +
                        " WHERE " + Constants.COL_TIME + " LIKE '" + yesterday + "%'" +
                        " ORDER BY " + Constants.COL_ID + " DESC LIMIT 5", null);
                if (cursor.getCount() == 0) {
                    return "昨天没有记录心情，今天别忘了记录哦 🌱";
                }
                int pos = 0, neg = 0;
                while (cursor.moveToNext()) {
                    if (cursor.getInt(0) == 1) pos++;
                    else neg++;
                }
                if (pos >= neg) {
                    return "昨天你看起来状态不错，今天继续保持";
                } else {
                    return "昨天你似乎有些低落，今天感觉好点了吗？给自己一点温柔的时间";
                }
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        } catch (Exception e) {
            return "花 30 秒记录此刻的感受，持续追踪情绪变化";
        }
    }

    /** 发送每周情绪总结 */
    public static void showWeeklySummary(Context context, int totalRecords, int posCount, int negCount) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        String summary;
        if (totalRecords == 0) {
            summary = "本周暂无记录，开始你的第一篇情绪日记吧";
        } else {
            float posRatio = totalRecords > 0 ? (float) posCount / totalRecords * 100 : 0;
            summary = String.format("本周 %d 条记录 · 积极情绪占 %.0f%%\n%s",
                    totalRecords, posRatio,
                    posRatio >= 60 ? "保持这份好状态" : "下周会更好，记得照顾自己");
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_WEEKLY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("本周情绪回顾")
                .setContentText("本周 " + totalRecords + " 条记录，点击查看详情")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(createOpenAppIntent(context));

        nm.notify(NOTIFY_WEEKLY_ID, builder.build());
    }

    /** 调度每日提醒 AlarmManager */
    public static void scheduleDailyReminder(Context context, int hour, int minute) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.example.emoscope.DAILY_REMINDER");
        PendingIntent pending = PendingIntent.getBroadcast(
                context, REQUEST_DAILY, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        // 如果今天的时间已过，推迟到明天
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        long triggerMillis = cal.getTimeInMillis();
        // Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限才能使用精确闹钟
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending);
            } else {
                // 降级：无精确闹钟权限时使用 setWindow（15 分钟窗口）
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerMillis - 15 * 60 * 1000L,
                        30 * 60 * 1000L, pending);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pending);
        }
    }

    /** 取消每日提醒 */
    public static void cancelDailyReminder(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.example.emoscope.DAILY_REMINDER");
        PendingIntent pending = PendingIntent.getBroadcast(
                context, REQUEST_DAILY, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pending);
    }

    /** 调度每周日 21:00 周报 */
    public static void scheduleWeeklySummary(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.example.emoscope.WEEKLY_SUMMARY");
        PendingIntent pending = PendingIntent.getBroadcast(
                context, REQUEST_WEEKLY, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        cal.set(Calendar.HOUR_OF_DAY, 21);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.WEEK_OF_MONTH, 1);
        }

        long triggerMillis = cal.getTimeInMillis();
        // Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限才能使用精确闹钟
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending);
            } else {
                // 降级：无精确闹钟权限时使用 setWindow
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerMillis - 30 * 60 * 1000L,
                        60 * 60 * 1000L, pending);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pending);
        }
    }

    /** 取消每周报告 */
    public static void cancelWeeklySummary(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.example.emoscope.WEEKLY_SUMMARY");
        PendingIntent pending = PendingIntent.getBroadcast(
                context, REQUEST_WEEKLY, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pending);
    }

    /** 重新调度所有已启用的提醒 — 开机时调用 */
    public static void rescheduleIfNeeded(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(Constants.KEY_NOTIFY_DAILY, false)) {
            int hour = prefs.getInt(Constants.KEY_NOTIFY_HOUR, 20);
            int minute = prefs.getInt(Constants.KEY_NOTIFY_MINUTE, 0);
            scheduleDailyReminder(context, hour, minute);
        }
        if (prefs.getBoolean(Constants.KEY_NOTIFY_WEEKLY, false)) {
            scheduleWeeklySummary(context);
        }
    }

    /** 创建打开 App 的 PendingIntent */
    private static PendingIntent createOpenAppIntent(Context context) {
        Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) return null;
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
