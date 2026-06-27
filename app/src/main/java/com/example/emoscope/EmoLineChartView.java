package com.example.emoscope;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Shader;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * 情绪波动曲线图 — 分段 Catmull-Rom 样条。
 *
 * 核心特性：
 *  1. 自动检测情绪拐点（局部极值），将曲线切分为独立段
 *  2. 每段按趋势着色：上升🟢 / 下降🔴 / 平稳🟣
 *  3. 情绪区域渐变填充（高涨区/平稳区/低谷区）
 *  4. 拐点以菱形标记，普通点以小圆标记
 *  5. 所有颜色通过主题属性动态获取，支持深色模式
 */
public class EmoLineChartView extends View {

    private final List<Float> data;
    private final List<String> dateLabels; // X轴日期标签
    private final List<Segment> segments = new ArrayList<>();

    // 坐标空间
    private float px, py, graphW, graphH;

    // ── Paint 池 ──────────────────────────────────────────────
    private final Paint linePaint, fillPaint, pointPaint;
    private final Paint gridPaint, zonePaint, labelPaint;
    private final Paint inflectionPaint, trendPaint;
    private final Paint refPaint70, refPaint30;  // 预分配参考线 Paint
    private final Path linePath, fillPath, trendPath;

    // ── 主题感知颜色 ──────────────────────────────────────────
    private final int greenLight, greenDark;
    private final int redLight, redDark;
    private final int purpleLight, purpleDark;
    private final int gridColor, textColor;

    /** 情绪段 — 一段连续的单调趋势 */
    private static class Segment {
        int startIdx, endIdx;
        int trend;                   // 1=上升, -1=下降, 0=平稳
        int colorStart, colorEnd;

        Segment(int s, int e, int t, int cs, int ce) {
            startIdx = s; endIdx = e; trend = t; colorStart = cs; colorEnd = ce;
        }
    }

    public EmoLineChartView(Context context, List<Float> d, List<String> dates) {
        super(context);
        this.data = d;
        this.dateLabels = dates;

        // 从颜色资源获取主题感知颜色（自动适配浅色/深色模式）
        greenLight = ContextCompat.getColor(context, R.color.positive_green);
        greenDark  = ContextCompat.getColor(context, R.color.positive_green_dark);
        redLight   = ContextCompat.getColor(context, R.color.danger_red);
        redDark    = ContextCompat.getColor(context, R.color.danger_red_dark);
        purpleLight = ContextCompat.getColor(context, R.color.accent_purple);
        purpleDark  = ContextCompat.getColor(context, R.color.accent_purple_dark);

        // 网格和文字颜色从 Material 主题属性获取
        gridColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOutlineVariant,
                Color.GRAY);
        textColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                Color.GRAY);

        linePath = new Path();
        fillPath = new Path();
        trendPath = new Path();

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setPathEffect(new CornerPathEffect(12f));

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setStyle(Paint.Style.FILL);

        inflectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inflectionPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1.5f);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{8f, 6f}, 0));

        zonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        zonePaint.setStyle(Paint.Style.FILL);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(textColor);
        labelPaint.setTextSize(10.5f * getResources().getDisplayMetrics().scaledDensity);
        labelPaint.setAntiAlias(true);

        // 移动平均趋势线 — 预分配避免 onDraw 中对象创建
        trendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trendPaint.setStyle(Paint.Style.STROKE);
        trendPaint.setStrokeWidth(2.5f);
        trendPaint.setStrokeCap(Paint.Cap.ROUND);
        trendPaint.setColor(0x60FFFFFF);
        trendPaint.setPathEffect(new DashPathEffect(new float[]{10f, 6f}, 0));

        // 参考线 Paint — 预分配
        float density = getResources().getDisplayMetrics().density;
        refPaint70 = new Paint(Paint.ANTI_ALIAS_FLAG);
        refPaint70.setStyle(Paint.Style.STROKE);
        refPaint70.setStrokeWidth(1.5f * density);
        refPaint70.setColor(0x4010B981);
        refPaint70.setPathEffect(new DashPathEffect(new float[]{8, 6}, 0));

        refPaint30 = new Paint(Paint.ANTI_ALIAS_FLAG);
        refPaint30.setStyle(Paint.Style.STROKE);
        refPaint30.setStrokeWidth(1.5f * density);
        refPaint30.setColor(0x40EF4444);
        refPaint30.setPathEffect(new DashPathEffect(new float[]{8, 6}, 0));

        detectSegments();
    }

    // ── 拐点检测 ──────────────────────────────────────────────
    private void detectSegments() {
        if (data == null || data.size() < 3) {
            if (data != null && !data.isEmpty()) {
                int cs, ce;
                float val = data.get(0);
                if (val >= 65)        { cs = greenLight; ce = greenDark; }
                else if (val <= 35)   { cs = redLight;   ce = redDark; }
                else                  { cs = purpleLight; ce = purpleDark; }
                segments.add(new Segment(0, data.size() - 1, 0, cs, ce));
            }
            return;
        }

        List<Integer> breakpoints = new ArrayList<>();
        breakpoints.add(0);

        int window = Math.max(2, data.size() / 15);
        for (int i = window; i < data.size() - window; i++) {
            float leftSlope = 0, rightSlope = 0;
            for (int j = 1; j <= window; j++) {
                leftSlope  += data.get(i) - data.get(i - j);
                rightSlope += data.get(i + j) - data.get(i);
            }

            if (leftSlope * rightSlope < 0 && Math.abs(leftSlope) > 5f && Math.abs(rightSlope) > 5f) {
                if (i - breakpoints.get(breakpoints.size() - 1) >= 2) {
                    breakpoints.add(i);
                }
            }
        }
        breakpoints.add(data.size() - 1);

        for (int i = 0; i < breakpoints.size() - 1; i++) {
            int start = breakpoints.get(i);
            int end = breakpoints.get(i + 1);
            if (end <= start) continue;

            float firstVal = data.get(start);
            float lastVal = data.get(end);
            float delta = lastVal - firstVal;

            int trend;
            int cs, ce;
            if (delta > 4f) {
                trend = 1; cs = greenLight; ce = greenDark;
            } else if (delta < -4f) {
                trend = -1; cs = redLight; ce = redDark;
            } else {
                trend = 0; cs = purpleLight; ce = purpleDark;
            }
            segments.add(new Segment(start, end, trend, cs, ce));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        px = 48f * getResources().getDisplayMetrics().density;
        py = 22f * getResources().getDisplayMetrics().density;
        graphW = w - px - 28f * getResources().getDisplayMetrics().density;
        graphH = h - py * 2 - 10f * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null || data.isEmpty() || graphW <= 0) return;

        float width = getWidth();

        drawEmotionZones(canvas, width);
        drawGrid(canvas, width);
        drawAverageGuide(canvas);
        if (dateLabels != null && !dateLabels.isEmpty()) drawDateLabels(canvas);

        int totalPoints = data.size();
        float stepX = totalPoints > 1 ? graphW / (totalPoints - 1) : 0;

        for (Segment seg : segments) {
            drawSegmentCurve(canvas, seg, stepX, totalPoints);
        }

        drawDataMarkers(canvas, stepX, totalPoints);
        if (totalPoints >= 7) drawTrendLine(canvas, stepX, totalPoints);
    }

    // ── 情绪区域着色 ──────────────────────────────────────────
    private void drawEmotionZones(Canvas canvas, float width) {
        float highY = py + graphH * 0.30f;
        float lowY  = py + graphH * 0.70f;

        // 高涨区 (>=75): 淡绿
        zonePaint.setShader(new LinearGradient(0, py, 0, highY,
                (greenLight & 0x00FFFFFF) | 0x18000000,
                (greenLight & 0x00FFFFFF) | 0x02000000,
                Shader.TileMode.CLAMP));
        canvas.drawRect(px, py, width, highY, zonePaint);

        // 低谷区 (<=25): 淡红
        zonePaint.setShader(new LinearGradient(0, lowY, 0, getHeight() - py,
                (redLight & 0x00FFFFFF) | 0x02000000,
                (redLight & 0x00FFFFFF) | 0x14000000,
                Shader.TileMode.CLAMP));
        canvas.drawRect(px, lowY, width, getHeight() - py, zonePaint);

        zonePaint.setShader(null);
    }

    // ── 网格 + Y轴标签 + 参考线 ─────────────────────────────────
    private void drawGrid(Canvas canvas, float width) {
        float[] yLevels = {0.30f, 0.50f, 0.70f};
        String[] labels = {"高 70", "中 50", "低 30"};

        for (int i = 0; i < yLevels.length; i++) {
            float y = py + graphH * yLevels[i];
            canvas.drawLine(px, y, px + graphW, y, gridPaint);
            canvas.drawText(labels[i], 2f * getResources().getDisplayMetrics().density,
                    y + 4f, labelPaint);
        }

        // 参考线：积极线(70) + 关注线(30) — 使用预分配 Paint
        float refY70 = py + graphH * 0.3f;
        canvas.drawLine(px, refY70, px + graphW, refY70, refPaint70);
        float refY30 = py + graphH * 0.7f;
        canvas.drawLine(px, refY30, px + graphW, refY30, refPaint30);
    }

    private void drawAverageGuide(Canvas canvas) {
        if (data == null || data.size() < 2) return;
        float sum = 0f;
        for (float value : data) sum += value;
        float avg = sum / data.size();
        float y = py + graphH - (avg / 100f * graphH);

        Paint avgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        avgPaint.setStyle(Paint.Style.STROKE);
        avgPaint.setStrokeWidth(1.2f * getResources().getDisplayMetrics().density);
        avgPaint.setColor(0x707C6EE6);
        avgPaint.setPathEffect(new DashPathEffect(new float[]{12f, 10f}, 0));
        canvas.drawLine(px, y, px + graphW, y, avgPaint);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(textColor);
        text.setTextSize(10f * getResources().getDisplayMetrics().scaledDensity);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("均值 " + Math.round(avg), px + graphW, y - 6f, text);
    }

    // ── X轴日期标签 ─────────────────────────────────────────────
    private void drawDateLabels(Canvas canvas) {
        if (dateLabels == null || dateLabels.isEmpty()) return;

        int maxLabels = Math.min(dateLabels.size(), 4);
        float step = dateLabels.size() > 1 ? graphW / (dateLabels.size() - 1) : 0;
        int interval = Math.max(2, dateLabels.size() / maxLabels);

        Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        datePaint.setColor(textColor);
        datePaint.setTextSize(10f * getResources().getDisplayMetrics().scaledDensity);
        datePaint.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < dateLabels.size(); i += interval) {
            float x = px + i * step;
            float y = py + graphH + 16f * getResources().getDisplayMetrics().density;
            // 只显示 MM-dd 格式
            String label = dateLabels.get(i);
            if (label.length() > 5) label = label.substring(5);
            canvas.drawText(label, x, y, datePaint);
        }
    }

    // ── 单段 Catmull-Rom 曲线 ────────────────────────────────
    private void drawSegmentCurve(Canvas canvas, Segment seg, float stepX, int totalPoints) {
        if (seg.endIdx - seg.startIdx < 0) return;

        int drawStart = Math.max(0, seg.startIdx - 1);
        int drawEnd   = Math.min(totalPoints - 1, seg.endIdx + 1);

        List<PointF> pts = new ArrayList<>();
        for (int i = drawStart; i <= drawEnd; i++) {
            float x = px + i * stepX;
            float y = py + graphH - (data.get(i) / 100f * graphH);
            pts.add(new PointF(x, y));
        }

        if (pts.size() == 1) {
            linePaint.setColor(seg.colorStart);
            linePaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(pts.get(0).x, pts.get(0).y, 7f, linePaint);
            linePaint.setStyle(Paint.Style.STROKE);
            return;
        }

        linePath.reset();
        fillPath.reset();

        linePath.moveTo(pts.get(0).x, pts.get(0).y);
        fillPath.moveTo(pts.get(0).x, py + graphH);
        fillPath.lineTo(pts.get(0).x, pts.get(0).y);

        float tension = 0.3f;
        for (int i = 0; i < pts.size() - 1; i++) {
            PointF p0 = i == 0 ? pts.get(0) : pts.get(i - 1);
            PointF p1 = pts.get(i);
            PointF p2 = pts.get(i + 1);
            PointF p3 = i + 2 < pts.size() ? pts.get(i + 2) : pts.get(i + 1);

            float d1x = (p2.x - p0.x) * tension;
            float d1y = (p2.y - p0.y) * tension;
            float d2x = (p3.x - p1.x) * tension;
            float d2y = (p3.y - p1.y) * tension;

            linePath.cubicTo(p1.x + d1x, p1.y + d1y,
                    p2.x - d2x, p2.y - d2y, p2.x, p2.y);
            fillPath.cubicTo(p1.x + d1x, p1.y + d1y,
                    p2.x - d2x, p2.y - d2y, p2.x, p2.y);
        }

        fillPath.lineTo(pts.get(pts.size() - 1).x, py + graphH);
        fillPath.close();

        // 填充
        int fillAlpha = 0x26;
        int fillStart = (seg.colorStart & 0x00FFFFFF) | (fillAlpha << 24);
        int fillEnd   = (seg.colorEnd   & 0x00FFFFFF) | (fillAlpha << 24);
        fillPaint.setShader(new LinearGradient(
                pts.get(0).x, py, pts.get(pts.size() - 1).x, py + graphH,
                fillStart, fillEnd, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);

        // 描边
        linePaint.setShader(new LinearGradient(
                pts.get(0).x, py, pts.get(pts.size() - 1).x, py + graphH,
                seg.colorStart, seg.colorEnd, Shader.TileMode.CLAMP));
        canvas.drawPath(linePath, linePaint);

        linePaint.setShader(null);
        fillPaint.setShader(null);
    }

    // ── 7日移动平均趋势线 ───────────────────────────────────
    private void drawTrendLine(Canvas canvas, float stepX, int totalPoints) {
        int window = Math.min(7, totalPoints);
        float[] smoothed = new float[totalPoints];
        for (int i = 0; i < totalPoints; i++) {
            float sum = 0; int count = 0;
            int start = Math.max(0, i - window / 2);
            int end = Math.min(totalPoints - 1, i + window / 2);
            for (int j = start; j <= end; j++) { sum += data.get(j); count++; }
            smoothed[i] = sum / count;
        }
        trendPath.reset();
        for (int i = 0; i < totalPoints; i++) {
            float x = px + i * stepX;
            float y = py + graphH - (smoothed[i] / 100f * graphH);
            if (i == 0) trendPath.moveTo(x, y);
            else trendPath.lineTo(x, y);
        }
        // 浅色覆盖线 + 白色底层发光效果
        trendPaint.setColor(0x60FFFFFF);
        canvas.drawPath(trendPath, trendPaint);
    }

    // ── 数据点标记 ────────────────────────────────────────────
    private void drawDataMarkers(Canvas canvas, float stepX, int totalPoints) {
        boolean[] isInflection = new boolean[totalPoints];
        for (Segment seg : segments) {
            if (seg.startIdx > 0) isInflection[seg.startIdx] = true;
        }

        for (int i = 0; i < totalPoints; i++) {
            float x = px + i * stepX;
            float y = py + graphH - (data.get(i) / 100f * graphH);

            if (isInflection[i]) {
                inflectionPaint.setColor(0xEEFFFFFF);
                drawDiamond(canvas, x, y, 6.5f, inflectionPaint);
                inflectionPaint.setColor(getSegmentColorAt(i));
                drawDiamond(canvas, x, y, 4f, inflectionPaint);
            } else {
                pointPaint.setColor(0xEEFFFFFF);
                canvas.drawCircle(x, y, 4.5f, pointPaint);
                pointPaint.setColor(getSegmentColorAt(i));
                canvas.drawCircle(x, y, 3f, pointPaint);
            }
        }
    }

    private int getSegmentColorAt(int index) {
        for (Segment seg : segments) {
            if (index >= seg.startIdx && index <= seg.endIdx) {
                return seg.trend >= 0 ? greenLight : redLight;
            }
        }
        return purpleLight;
    }

    private void drawDiamond(Canvas canvas, float cx, float cy, float r, Paint paint) {
        Path diamond = new Path();
        diamond.moveTo(cx, cy - r);
        diamond.lineTo(cx + r, cy);
        diamond.lineTo(cx, cy + r);
        diamond.lineTo(cx - r, cy);
        diamond.close();
        canvas.drawPath(diamond, paint);
    }
}
