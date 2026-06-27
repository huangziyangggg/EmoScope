package com.example.emoscope;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * 呼吸引导动画引擎 — 从 MainActivity 提取。
 * 支持 2 种呼吸模式, 动态构建缩放动画序列含触觉引导回调。
 */
public class BreathingEngine {

    public interface BreathCallback {
        /** 阶段切换 — 更新提示文字 */
        void onPhaseChange(String phaseText);
        /**
         * 阶段开始 — 携带完整阶段信息供触觉控制器生成对应波形。
         * @param phaseIndex 0=吸气, 1=屏息, 2=呼气, 3=屏息
         * @param durationMs 阶段持续时长（毫秒）
         * @param phaseText  阶段提示文字
         */
        void onPhaseStart(int phaseIndex, long durationMs, String phaseText);
        /** 呼吸循环结束 (用于重新开始) */
        void onCycleEnd();
    }

    private final View breathCircle;
    private final BreathCallback callback;
    private AnimatorSet animator;
    private boolean isRunning = false;

    public BreathingEngine(View breathCircle, BreathCallback callback) {
        this.breathCircle = breathCircle;
        this.callback = callback;
    }

    public boolean isRunning() { return isRunning; }

    /** 启动指定模式的呼吸引导 */
    public void start(int mode) {
        if (isRunning) return;
        isRunning = true;

        breathCircle.setScaleX(1f);
        breathCircle.setScaleY(1f);

        long[] phases = Constants.BREATH_PHASES[mode];
        String[] phaseTexts = {
            "缓缓吸气…", "屏住呼吸…", "慢慢呼出…", "屏住呼吸…"
        };

        AnimatorSet breathCycle = new AnimatorSet();
        List<Animator> animators = new ArrayList<>();

        float currentScale = 1f;
        for (int i = 0; i < phases.length; i++) {
            if (phases[i] <= 0) continue;

            long duration = phases[i];
            boolean isHold = (i == 1 || i == 3);
            boolean isInhale = (i == 0);
            boolean isExhale = (i == 2);

            float targetScale;
            if (isInhale) targetScale = 4.0f;
            else if (isExhale) targetScale = 1f;
            else targetScale = currentScale;

            String phaseText = phaseTexts[Math.min(i, phaseTexts.length - 1)];

            final int phaseIdx = i; // 0=吸气, 1=屏息, 2=呼气, 3=屏息

            if (isHold) {
                ObjectAnimator holdX = ObjectAnimator.ofFloat(breathCircle, "scaleX",
                        currentScale, currentScale * 1.05f, currentScale);
                ObjectAnimator holdY = ObjectAnimator.ofFloat(breathCircle, "scaleY",
                        currentScale, currentScale * 1.05f, currentScale);
                holdX.setDuration(duration);
                holdY.setDuration(duration);
                holdX.setInterpolator(new AccelerateDecelerateInterpolator());
                holdY.setInterpolator(new AccelerateDecelerateInterpolator());
                holdX.addUpdateListener(a -> {
                    if (a.getAnimatedFraction() >= 0.01f && a.getAnimatedFraction() < 0.02f) {
                        callback.onPhaseChange(phaseText);
                        callback.onPhaseStart(phaseIdx, duration, phaseText);
                    }
                });
                AnimatorSet holdSet = new AnimatorSet();
                holdSet.playTogether(holdX, holdY);
                animators.add(holdSet);
            } else {
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(breathCircle, "scaleX", currentScale, targetScale);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(breathCircle, "scaleY", currentScale, targetScale);
                scaleX.setDuration(duration);
                scaleY.setDuration(duration);
                scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
                scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
                scaleX.addUpdateListener(a -> {
                    float frac = a.getAnimatedFraction();
                    if (frac >= 0.01f && frac < 0.02f) {
                        callback.onPhaseChange(phaseText);
                        callback.onPhaseStart(phaseIdx, duration, phaseText);
                    }
                });
                AnimatorSet scaleSet = new AnimatorSet();
                scaleSet.playTogether(scaleX, scaleY);
                animators.add(scaleSet);
                currentScale = targetScale;
            }
        }

        if (!animators.isEmpty()) {
            breathCycle.playSequentially(animators);
            breathCycle.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (isRunning) {
                        callback.onCycleEnd();
                        breathCycle.start();
                    }
                }
            });
            animator = breathCycle;
            breathCycle.start();
        }
    }

    /** 停止呼吸引导 */
    public void stop() {
        isRunning = false;
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
    }
}
