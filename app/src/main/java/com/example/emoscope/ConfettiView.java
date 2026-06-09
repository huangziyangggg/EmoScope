package com.example.emoscope;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 成就彩带粒子 — 解锁徽章时飘落的彩色碎片。
 */
public class ConfettiView extends View {

    private static class Particle {
        float x, y, vx, vy, size, rotation, rotSpeed;
        int color;
        Particle(float x, float y, float vx, float vy, float size, int color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.size = size; this.color = color;
            this.rotation = 0;
            this.rotSpeed = (float) (Math.random() * 20 - 10);
        }
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private ValueAnimator animator;
    private static final int[] COLORS = {
        0xFFB794F4, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF0EA5E9, 0xFFEF4444
    };

    public ConfettiView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setVisibility(GONE);
    }

    public void burst() {
        setVisibility(VISIBLE);
        particles.clear();
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        for (int i = 0; i < 60; i++) {
            float angle = (float) (Math.random() * Math.PI * 2);
            float speed = 3f + random.nextFloat() * 12f;
            particles.add(new Particle(
                cx, cy,
                (float) Math.cos(angle) * speed,
                (float) Math.sin(angle) * speed - 3f,
                4f + random.nextFloat() * 8f,
                COLORS[random.nextInt(COLORS.length)]
            ));
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2000);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.addUpdateListener(a -> {
            float gravity = 0.3f;
            for (Particle p : particles) {
                p.vy += gravity;
                p.x += p.vx;
                p.y += p.vy;
                p.rotation += p.rotSpeed;
            }
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                setVisibility(GONE);
                particles.clear();
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Particle p : particles) {
            paint.setColor(p.color);
            paint.setAlpha(200);
            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.rotation);
            canvas.drawRect(-p.size / 2f, -p.size / 4f, p.size / 2f, p.size / 4f, paint);
            canvas.restore();
        }
    }
}
