package com.example.emoscope;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * 呼吸节奏触觉引导控制器。
 * 模仿 Apple Watch 呼吸应用的 Taptic Engine 节奏，
 * 用振动画出呼吸曲线——吸气强度递增、屏息柔和锚点、呼气强度递减。
 *
 * 设计原则（KISS）：
 * - 一套波形参数适配两种呼吸模式（方块呼吸 / 4-7-8）
 * - 线性马达设备获得清晰节奏感，转子马达退化但仍有节奏提示
 * - 受全局 KEY_HAPTIC 开关控制
 */
public class BreathingHapticController {

    private final Vibrator vibrator;

    // 振动波形参数（毫秒）
    private static final int TAP_MS = 15;              // 单次轻敲时长
    private static final int GAP_MS = 8;               // 轻敲间默认停顿
    private static final int TAPS_PER_PHASE = 10;      // 每阶段轻敲数
    private static final int AMP_MIN = 40;             // 最小振幅（1-255）
    private static final int AMP_MAX = 220;            // 最大振幅
    private static final int HOLD_PULSE_AMP = 70;      // 屏息脉冲振幅
    private static final long HOLD_PULSE_INTERVAL = 2000; // 屏息脉冲间隔

    private boolean enabled = true;

    public BreathingHapticController(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager)
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @param phaseIndex 0=吸气, 1=屏息, 2=呼气, 3=屏息
     * @param durationMs 阶段持续时长（毫秒）
     */
    public void playPhase(int phaseIndex, long durationMs) {
        if (vibrator == null || !enabled || durationMs <= 0) return;

        boolean isHold = (phaseIndex == 1 || phaseIndex == 3);
        if (isHold) {
            playHold(durationMs);
        } else {
            boolean ascending = (phaseIndex == 0);
            playBreathWave(durationMs, ascending);
        }
    }

    /** 立即停止所有触觉输出 */
    public void stop() {
        if (vibrator != null) vibrator.cancel();
    }

    // ── 呼吸波形（吸气/呼气）──────────────────────────────────────

    private void playBreathWave(long durationMs, boolean ascending) {
        int taps = TAPS_PER_PHASE;

        // 动态调整轻敲数以适应时长（方块呼吸 4s → 10敲，4-7-8 吸气 4s/呼气 8s）
        taps = (int) Math.max(5, Math.min(20, durationMs / 400));

        // 构建波形数组：[振动, 暂停, 振动, 暂停, ...]
        int len = taps * 2 - 1;
        long[] timings = new long[len];
        int[] amplitudes = new int[len];

        // 计算暂停时长使得轻敲均匀分布
        long occupied = taps * TAP_MS + (taps - 1) * GAP_MS;
        long extraSpacing = Math.max(0, (durationMs - occupied) / (taps - 1 + 1));

        for (int i = 0; i < taps; i++) {
            float progress = taps > 1 ? (float) i / (taps - 1) : 0.5f;
            int amp = ascending
                    ? (int) (AMP_MIN + (AMP_MAX - AMP_MIN) * progress)
                    : (int) (AMP_MAX - (AMP_MAX - AMP_MIN) * progress);

            int tapIdx = i * 2;
            timings[tapIdx] = TAP_MS;
            amplitudes[tapIdx] = amp;

            if (i < taps - 1) {
                timings[tapIdx + 1] = GAP_MS + extraSpacing;
                amplitudes[tapIdx + 1] = 0;
            }
        }

        vibrateWaveform(timings, amplitudes);
    }

    // ── 屏息波形 ──────────────────────────────────────────────────

    private void playHold(long durationMs) {
        int pulses = Math.max(1, (int) (durationMs / HOLD_PULSE_INTERVAL));
        long gap = durationMs / pulses;

        int len = pulses * 2 - 1;
        long[] timings = new long[len];
        int[] amplitudes = new int[len];

        for (int i = 0; i < pulses; i++) {
            int idx = i * 2;
            timings[idx] = 25;            // 屏息脉冲稍长，更柔和
            amplitudes[idx] = HOLD_PULSE_AMP;

            if (i < pulses - 1) {
                timings[idx + 1] = gap - 25;
                amplitudes[idx + 1] = 0;
            }
        }

        vibrateWaveform(timings, amplitudes);
    }

    // ── 底层振动调用 ──────────────────────────────────────────────

    private void vibrateWaveform(long[] timings, int[] amplitudes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+: 精确波形
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
        } else {
            // API 24-25 回退: 简单振动（极少设备）
            long total = 0;
            for (long t : timings) total += t;
            vibrator.vibrate(Math.min(total, 5000));
        }
    }
}
