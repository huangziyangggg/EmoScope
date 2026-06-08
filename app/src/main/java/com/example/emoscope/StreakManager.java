package com.example.emoscope;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 连续打卡天数管理器。
 * 统一 MainActivity 和 HistoryFragment 中重复的 streak 计算逻辑。
 */
public final class StreakManager {

    private StreakManager() {}

    private static final SimpleDateFormat SDF_DAY = new SimpleDateFormat("MM-dd", Locale.getDefault());

    /**
     * 更新并返回连续打卡天数。
     * 调用此方法后自动写入 SharedPreferences。
     */
    public static int updateAndGetStreak(SharedPreferences prefs) {
        String today;
        synchronized (SDF_DAY) { today = SDF_DAY.format(new Date()); }
        String lastDate = prefs.getString(Constants.KEY_LAST_RECORD_DATE, "");
        int streak = prefs.getInt(Constants.KEY_STREAK_COUNT, 0);

        if (today.equals(lastDate)) return streak;

        if (!lastDate.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
                Date last = sdf.parse(lastDate);
                Date now = sdf.parse(today);
                long diff = (now.getTime() - last.getTime()) / (1000 * 60 * 60 * 24);
                if (diff == 1) streak++;
                else streak = 1;
            } catch (Exception e) { streak = 1; }
        } else {
            streak = 1;
        }

        prefs.edit().putString(Constants.KEY_LAST_RECORD_DATE, today)
                .putInt(Constants.KEY_STREAK_COUNT, streak).apply();
        return streak;
    }

    /**
     * 获取格式化打卡显示文字。
     * @return 如 "连续 7 天" 或 null（打卡天数不足不显示）
     */
    public static String getStreakDisplay(Context context, SharedPreferences prefs) {
        int streak = prefs.getInt(Constants.KEY_STREAK_COUNT, 0);
        if (streak > 1) {
            return String.format("连续 %d 天", streak);
        }
        return null;
    }
}
