package com.example.emoscope;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * 语音录制声波纹 — 5根跳动的竖条，制造"正在听你说话"的视觉效果。
 */
public class VoiceWaveView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] barHeights = new float[5];
    private final float[] targetHeights = new float[5];
    private final Random random = new Random();
    private ValueAnimator animator;
    private boolean isAnimating = false;
    private int waveColor = 0xFFFFFFFF;
    private float barWidth, gap, maxHeight;

    public VoiceWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        waveColor = androidx.core.content.ContextCompat.getColor(context, R.color.grad_btn_start);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float totalGap = w * 0.15f;
        barWidth = (w - totalGap) / 5f;
        gap = totalGap / 4f;
        maxHeight = h * 0.9f;
    }

    public void start() {
        if (isAnimating) return;
        isAnimating = true;
        setVisibility(VISIBLE);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(Long.MAX_VALUE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            for (int i = 0; i < 5; i++) {
                if (Math.abs(barHeights[i] - targetHeights[i]) < 0.02f) {
                    targetHeights[i] = 0.2f + random.nextFloat() * 0.8f;
                }
                barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.15f;
            }
            invalidate();
        });
        animator.start();
    }

    public void stop() {
        isAnimating = false;
        if (animator != null) animator.cancel();
        for (int i = 0; i < 5; i++) barHeights[i] = 0f;
        invalidate();
        setVisibility(GONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerY = getHeight() / 2f;
        barPaint.setColor(waveColor);

        for (int i = 0; i < 5; i++) {
            float h = barHeights[i] * maxHeight;
            float left = i * (barWidth + gap);
            float top = centerY - h / 2f;
            RectF rect = new RectF(left, top, left + barWidth, top + h);
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, barPaint);
        }
    }
}
