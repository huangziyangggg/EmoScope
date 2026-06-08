package com.example.emoscope;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局未捕获异常处理器 — 将崩溃堆栈写入应用内部存储。
 * 轻量级实现，无任何外部依赖，无需存储权限。
 *
 * 使用: CrashHandler.install(context) 在 onCreate 中调用
 */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {

    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final File crashDir;

    private CrashHandler(Thread.UncaughtExceptionHandler defaultHandler, File crashDir) {
        this.defaultHandler = defaultHandler;
        this.crashDir = crashDir;
    }

    /** 安装全局崩溃处理器 — 日志写入应用私有目录，无需权限 */
    public static void install(Context context) {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof CrashHandler) return;

        File dir = new File(context.getFilesDir(), "crashes");
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(current, dir));
        Log.i(Constants.TAG, "CrashHandler installed at " + dir.getAbsolutePath());
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            writeCrashLog(thread, throwable);
        } catch (Exception ignored) {
            // 崩溃日志写入失败不能掩盖原始异常
        } finally {
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }
    }

    private void writeCrashLog(Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("=== EmoScope Crash Report ===");
        pw.println("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        pw.println("Thread: " + thread.getName());
        pw.println();
        throwable.printStackTrace(pw);
        pw.flush();

        try {
            if (!crashDir.exists()) {
                crashDir.mkdirs();
            }

            // 最多保留 5 个崩溃日志
            File[] existing = crashDir.listFiles();
            if (existing != null && existing.length >= 5) {
                java.util.Arrays.sort(existing,
                        (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i <= existing.length - 5; i++) {
                    existing[i].delete();
                }
            }

            String fileName = "crash_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) +
                    ".log";
            File file = new File(crashDir, fileName);
            FileWriter writer = new FileWriter(file);
            writer.write(sw.toString());
            writer.close();
            Log.e(Constants.TAG, "Crash log written: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(Constants.TAG, "Failed to write crash log", e);
        }
    }
}
