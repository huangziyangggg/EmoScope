package com.example.emoscope;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Centralizes destructive local-data operations so privacy controls do not
 * depend on UI implementation details.
 */
public final class LocalDataManager {

    private LocalDataManager() {
    }

    public static void clearAllLocalData(Context context) {
        NotificationHelper.cancelDailyReminder(context);
        NotificationHelper.cancelWeeklySummary(context);

        new EmoDatabaseHelper(context).clearAllRecords();
        new SecureStorage(context).clear();

        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
