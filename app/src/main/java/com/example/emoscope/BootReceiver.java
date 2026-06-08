package com.example.emoscope;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启广播接收器 — 重新注册所有已启用的定时提醒。
 * 设备重启后 AlarmManager 中的定时任务会丢失，需要重新调度。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(Constants.TAG, "BootReceiver: rescheduling reminders");
            NotificationHelper.rescheduleIfNeeded(context);
        }
    }
}
