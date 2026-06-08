package com.example.emoscope;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 声纳涟漪 — 从中心不断扩散的圆环，制造"正在接收声音"的视觉效果。
 */
public class SonarRippleView extends View {

    private static class Ripple {
        float radius = 0f;
        float alpha = 1f;
        final float maxRadius;
        Ripple(float maxR) { this.maxRadius = maxR; }
    }

    private final List<Ripple> ripples = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private boolean isAnimating = false;
    private int rippleColor = 0xFF7C5CFC;

    public SonarRippleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        rippleColor = androidx.core.content.ContextCompat.getColor(context, R.color.grad_btn_start);
    }

    public void start() {
        if (isAnimating) return;
        isAnimating = true;
        setVisibility(VISIBLE);
        ripples.clear();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(Long.MAX_VALUE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            float maxR = Math.min(getWidth(), getHeight()) / 2f;
            // 每 800ms 产生一个新涟漪
            if (ripples.isEmpty() || ripples.get(ripples.size() - 1).radius > maxR * 0.3f) {
                ripples.add(new Ripple(maxR));
            }
            Iterator<Ripple> it = ripples.iterator();
            while (it.hasNext()) {
                Ripple r = it.next();
                r.radius += maxR * 0.02f;
                r.alpha = 1f - (r.radius / r.maxRadius);
                if (r.alpha <= 0) it.remove();
            }
            invalidate();
        });
        animator.start();
    }

    public void stop() {
        isAnimating = false;
        if (animator != null) animator.cancel();
        ripples.clear();
        invalidate();
        setVisibility(GONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        for (Ripple r : ripples) {
            paint.setColor(rippleColor);
            paint.setAlpha((int) (r.alpha * 80));
            paint.setStrokeWidth(3f * r.alpha + 1f);
            canvas.drawCircle(cx, cy, r.radius, paint);
        }
    }
}
