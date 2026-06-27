package com.example.emoscope;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * 多层渐变呼吸环 — SOS 模式下显示 5 层同心涟漪，随呼吸节奏缩放。
 */
public class BreathingOverlayView extends View {

    private final Paint[] ringPaints = new Paint[5];
    private final float[] ringScales = {0.3f, 0.5f, 0.7f, 0.85f, 1.0f};
    private final float[] ringAlphas = {0.15f, 0.25f, 0.4f, 0.6f, 0.8f};
    private final int[] ringColors = {
        0xFFB794F4, 0xFFA78BFA, 0xFF9B8AF0, 0xFFC4B5FD, 0xFFDDD6FE
    };

    private float globalScale = 1f;
    private ValueAnimator pulseAnim;
    private boolean isAnimating = false;
    private final Paint centerPaint;
    private Shader[] ringShaders;
    private float lastCx = -1, lastCy = -1;

    public BreathingOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        for (int i = 0; i < 5; i++) {
            ringPaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            ringPaints[i].setStyle(Paint.Style.STROKE);
        }
        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(0xFFC4B5FD);
        centerPaint.setStyle(Paint.Style.FILL);
    }

    public void startBreathing(long phaseDuration) {
        if (isAnimating) return;
        isAnimating = true;
        setVisibility(VISIBLE);

        pulseAnim = ValueAnimator.ofFloat(0.5f, 2.0f, 0.5f);
        pulseAnim.setDuration(phaseDuration * 4); // 完整吸-呼循环
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnim.addUpdateListener(a -> {
            globalScale = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnim.start();
    }

    public void stopBreathing() {
        isAnimating = false;
        if (pulseAnim != null) { pulseAnim.cancel(); pulseAnim = null; }
        setVisibility(GONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxR = Math.min(cx, cy) * 0.85f;

        // 仅在尺寸变化时重建渐变（避免 onDraw 中反复创建对象）
        if (cx != lastCx || cy != lastCy || ringShaders == null) {
            lastCx = cx; lastCy = cy;
            ringShaders = new Shader[5];
            for (int i = 0; i < 5; i++) {
                float r = maxR * ringScales[i];
                ringShaders[i] = new RadialGradient(cx, cy, r,
                        ringColors[i], (ringColors[i] & 0x00FFFFFF) | 0x00000000,
                        Shader.TileMode.CLAMP);
            }
        }

        for (int i = 4; i >= 0; i--) {
            float r = maxR * ringScales[i] * globalScale;
            float alpha = ringAlphas[i] * (2f - Math.abs(globalScale - 1f));

            Paint p = ringPaints[i];
            p.setColor(ringColors[i]);
            p.setAlpha((int) (alpha * 255));
            p.setStrokeWidth(3f + i * 1.5f);
            p.setShader(ringShaders[i]);

            canvas.drawCircle(cx, cy, r, p);
        }

        // 中心发光点（复用预创建的 Paint）
        centerPaint.setAlpha((int) (100 * (2f - Math.abs(globalScale - 1f))));
        canvas.drawCircle(cx, cy, maxR * 0.08f * globalScale, centerPaint);
    }
}
