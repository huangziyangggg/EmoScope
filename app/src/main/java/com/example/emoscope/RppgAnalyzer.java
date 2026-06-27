package com.example.emoscope;

import android.util.Log;

import java.util.Arrays;

/**
 * 实验性 rPPG 远程光电容积脉搏波分析器。
 *
 * 从摄像头采集的面部 ROI 绿色通道均值中提取心率线索。
 * 信号处理管线：归一化 → 去趋势 → 带通滤波 → 自相关 → 峰值检测
 *
 * 重要：此为实验性辅助功能，不作为医疗诊断依据。
 * 结果受光照、面部移动、摄像头硬件等因素影响。
 */
public final class RppgAnalyzer {

    private static final String TAG = Constants.TAG + "_rPPG";

    /** 采样窗口（秒）— 至少需要此长度才能计算心率 */
    public static final int MIN_WINDOW_SEC = 12;
    public static final int MAX_WINDOW_SEC = 35;

    /** 预估相机帧率（Hz）— 实际采样率在process()中动态计算 */
    private static final float ESTIMATED_FRAME_RATE = 15f;

    /** 心率范围 (BPM) */
    private static final float HR_MIN = 42f;
    private static final float HR_MAX = 200f;

    /** 带通滤波频段 (Hz) */
    private static final float BP_LOW = 0.7f;
    private static final float BP_HIGH = 4.0f;

    /** 去趋势窗口（秒） */
    private static final float DETREND_WINDOW_SEC = 2.0f;

    // ── 信号缓冲（按预估帧率分配容量，防止低帧率设备溢出）──
    private final FloatRingBuffer greenBuffer;
    private final LongRingBuffer timestampBuffer;

    // ── 最近一次结果 ──
    private float latestBpm = -1f;
    private float latestSdnn = -1f;
    private float latestRmssd = -1f;
    private float latestConfidence = 0f;
    private int latestSignalQuality = 0; // 0-100
    private long lastProcessTime = 0;
    private static final long PROCESS_INTERVAL_MS = 3000;

    public RppgAnalyzer() {
        // 缓冲区容量按最大窗口×预估帧率分配；环形缓冲自动淘汰旧数据
        int capacity = (int) (MAX_WINDOW_SEC * ESTIMATED_FRAME_RATE) + 10;
        greenBuffer = new FloatRingBuffer(capacity);
        timestampBuffer = new LongRingBuffer(capacity);
    }

    /** 添加一帧的绿色通道均值样本 */
    public void addSample(float greenMean, long timestampMs) {
        greenBuffer.add(greenMean);
        timestampBuffer.add(timestampMs);
    }

    /** 是否有足够样本进行计算 */
    public boolean hasEnoughSamples() {
        if (greenBuffer.size() < 2) return false;
        long duration = timestampBuffer.newest() - timestampBuffer.oldest();
        return duration >= MIN_WINDOW_SEC * 1000L;
    }

    /** 是否应触发新一轮处理 */
    public boolean shouldProcess() {
        return hasEnoughSamples()
                && System.currentTimeMillis() - lastProcessTime >= PROCESS_INTERVAL_MS;
    }

    /** 执行信号处理，返回分析结果 */
    public RppgResult process() {
        lastProcessTime = System.currentTimeMillis();

        int n = greenBuffer.size();
        if (n < (int)(MIN_WINDOW_SEC * ESTIMATED_FRAME_RATE * 0.5f)) {
            return new RppgResult(-1f, -1f, -1f, 0f, 0, "样本不足，请保持面部在画面中并稳定15秒以上");
        }

        float[] signal = greenBuffer.toArray();
        long[] times = timestampBuffer.toArray();

        // 计算实际采样率
        float actualRate = 1000f * n / Math.max(1, times[n - 1] - times[0]);

        // 1. 归一化（去均值）
        float mean = mean(signal);
        for (int i = 0; i < n; i++) signal[i] -= mean;

        // 2. 去趋势（移动平均减法）
        int detrendSamples = Math.max(2, (int) (DETREND_WINDOW_SEC * actualRate));
        detrend(signal, detrendSamples);

        // 3. 归一化幅度
        float std = stddev(signal, mean(signal));
        if (std > 0.001f) {
            for (int i = 0; i < n; i++) signal[i] /= std;
        }

        // 4. 带通滤波 (0.7–4 Hz)
        bandpassFilter(signal, actualRate, BP_LOW, BP_HIGH);

        // 5. 计算信号质量 (信噪比简化评估)
        latestSignalQuality = estimateSignalQuality(signal, actualRate);

        // 6. 自相关求主导周期
        float dominantPeriod = autocorrelationPeak(signal, actualRate);
        if (dominantPeriod <= 0) {
            latestBpm = -1f;
            latestConfidence = Math.max(0, latestSignalQuality * 0.3f);
            latestSdnn = -1f;
            latestRmssd = -1f;
            return new RppgResult(-1f, -1f, -1f, latestConfidence, latestSignalQuality,
                    "暂未检测到稳定脉搏信号，请保持面部稳定、光照充足");
        }

        float bpm = 60f / dominantPeriod;
        if (bpm < HR_MIN || bpm > HR_MAX) {
            latestBpm = -1f;
            latestConfidence = latestSignalQuality * 0.2f;
            return new RppgResult(-1f, -1f, -1f, latestConfidence, latestSignalQuality,
                    "信号异常（" + String.format("%.0f", bpm) + " BPM超出正常范围），请重新尝试");
        }

        latestBpm = bpm;

        // 7. HRV 计算 — 峰值间期检测
        float[] ibiArray = detectInterBeatIntervals(signal, actualRate);
        if (ibiArray != null && ibiArray.length >= 2) {
            latestSdnn = computeSdnn(ibiArray);
            latestRmssd = computeRmssd(ibiArray);
        } else {
            latestSdnn = -1f;
            latestRmssd = -1f;
        }

        latestConfidence = computeConfidence(latestSignalQuality, actualRate, n);
        return new RppgResult(latestBpm, latestSdnn, latestRmssd,
                latestConfidence, latestSignalQuality, null);
    }

    /** 获取最近一次心率（BPM），未检测到返回 -1 */
    public float getLatestBpm() { return latestBpm; }

    /** 获取最近一次信号质量 (0-100) */
    public int getLatestSignalQuality() { return latestSignalQuality; }

    /** 获取最近一次置信度 (0-1) */
    public float getLatestConfidence() { return latestConfidence; }

    /** 获取最近一次 SDNN (ms)，未检测到返回 -1 */
    public float getLatestSdnn() { return latestSdnn; }

    /** 重置所有缓冲和历史结果 */
    public void reset() {
        greenBuffer.clear();
        timestampBuffer.clear();
        latestBpm = -1f;
        latestSdnn = -1f;
        latestRmssd = -1f;
        latestConfidence = 0f;
        latestSignalQuality = 0;
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部信号处理
    // ═══════════════════════════════════════════════════════════════

    private float mean(float[] arr) {
        float sum = 0;
        for (float v : arr) sum += v;
        return sum / arr.length;
    }

    private float stddev(float[] arr, float mean) {
        float sumSq = 0;
        for (float v : arr) {
            float d = v - mean;
            sumSq += d * d;
        }
        return (float) Math.sqrt(sumSq / arr.length);
    }

    /** 移动平均减法去趋势 */
    private void detrend(float[] signal, int window) {
        int n = signal.length;
        float[] trend = new float[n];
        for (int i = 0; i < n; i++) {
            int start = Math.max(0, i - window / 2);
            int end = Math.min(n, i + window / 2);
            float sum = 0;
            for (int j = start; j < end; j++) sum += signal[j];
            trend[i] = sum / (end - start);
        }
        for (int i = 0; i < n; i++) signal[i] -= trend[i];
    }

    /** 简化巴特沃斯二阶带通滤波器（双线性变换） */
    private void bandpassFilter(float[] signal, float sampleRate, float lowFreq, float highFreq) {
        int n = signal.length;
        if (n < 5) return;

        float dt = 1f / sampleRate;
        float omegaLow = (float) (2.0 * Math.PI * lowFreq);
        float omegaHigh = (float) (2.0 * Math.PI * highFreq);

        // 高通系数（一阶 RC）
        float alphaHigh = omegaLow * dt / (1f + omegaLow * dt);
        // 低通系数（一阶 RC）
        float alphaLow = omegaHigh * dt / (1f + omegaHigh * dt);

        // 先高通
        float prevHigh = signal[0];
        for (int i = 1; i < n; i++) {
            float filtered = alphaHigh * (prevHigh + signal[i] - signal[i - 1]);
            signal[i - 1] = prevHigh;
            prevHigh = filtered;
        }
        signal[n - 1] = prevHigh;

        // 再低通
        float prevLow = signal[0];
        for (int i = 1; i < n; i++) {
            signal[i] = alphaLow * signal[i] + (1f - alphaLow) * prevLow;
            prevLow = signal[i];
        }
    }

    /** 自相关求主导周期（秒） */
    private float autocorrelationPeak(float[] signal, float sampleRate) {
        int n = signal.length;
        int minLag = (int) (sampleRate / HR_MAX);  // 最快心率对应最小滞后
        int maxLag = (int) (sampleRate / HR_MIN);  // 最慢心率对应最大滞后
        if (maxLag >= n - 1) maxLag = n - 2;
        if (minLag < 1) minLag = 1;
        if (minLag >= maxLag) return -1f;

        float bestCorr = -Float.MAX_VALUE;
        int bestLag = -1;

        // 归一化自相关
        for (int lag = minLag; lag <= maxLag; lag++) {
            float sum = 0;
            int count = n - lag;
            if (count < 10) break;
            for (int i = 0; i < count; i++) {
                sum += signal[i] * signal[i + lag];
            }
            float corr = sum / count;
            if (corr > bestCorr) {
                bestCorr = corr;
                bestLag = lag;
            }
        }

        if (bestLag <= 0) return -1f;
        return (float) bestLag / sampleRate;
    }

    /** 峰值检测提取IBI序列 */
    private float[] detectInterBeatIntervals(float[] signal, float sampleRate) {
        int n = signal.length;
        // 自适应阈值
        float threshold = 0.3f * stddev(signal, 0);
        if (threshold < 0.1f) threshold = 0.1f;

        int minSamplesBetweenPeaks = (int) (sampleRate / HR_MAX);
        float[] peaks = new float[n / 2]; // 预分配
        int peakCount = 0;
        int lastPeak = -minSamplesBetweenPeaks;

        for (int i = 1; i < n - 1; i++) {
            if (signal[i] > threshold
                    && signal[i] > signal[i - 1]
                    && signal[i] > signal[i + 1]
                    && i - lastPeak >= minSamplesBetweenPeaks) {
                peaks[peakCount++] = i;
                lastPeak = i;
            }
        }

        if (peakCount < 2) return null;

        float[] ibis = new float[peakCount - 1];
        for (int i = 1; i < peakCount; i++) {
            ibis[i - 1] = (peaks[i] - peaks[i - 1]) / sampleRate * 1000f; // ms
        }
        return ibis;
    }

    private float computeSdnn(float[] ibis) {
        float mean = mean(ibis);
        float sumSq = 0;
        for (float ibi : ibis) {
            float d = ibi - mean;
            sumSq += d * d;
        }
        return (float) Math.sqrt(sumSq / ibis.length);
    }

    private float computeRmssd(float[] ibis) {
        float sumSq = 0;
        for (int i = 1; i < ibis.length; i++) {
            float d = ibis[i] - ibis[i - 1];
            sumSq += d * d;
        }
        return (float) Math.sqrt(sumSq / (ibis.length - 1));
    }

    /** 信号质量评估 — 基于通带内信号占比和稳定性 */
    private int estimateSignalQuality(float[] signal, float sampleRate) {
        int n = signal.length;
        if (n < 10) return 0;

        // 计算信号功率占比
        float totalPower = 0, bandPower = 0;
        // 简化FFT：使用Goertzel-style评估
        for (int i = 0; i < n; i++) {
            totalPower += signal[i] * signal[i];
        }

        // 时域稳定性评估
        float variation = 0;
        for (int i = 1; i < Math.min(n, 50); i++) {
            variation += Math.abs(signal[i] - signal[i - 1]);
        }
        variation /= Math.min(n - 1, 49);

        // 合并评分 (0-100)
        float stabilityScore = Math.max(0, 100 - variation * 200);
        float powerScore = totalPower > 0.01f ? 100 : totalPower * 10000;

        return (int) Math.max(0, Math.min(100, (stabilityScore * 0.6f + powerScore * 0.4f)));
    }

    private float computeConfidence(int signalQuality, float sampleRate, int sampleCount) {
        float durationScore = Math.min(1f,
                (sampleCount / sampleRate) / (float) MIN_WINDOW_SEC);
        float qualityScore = signalQuality / 100f;
        return Math.max(0, Math.min(1, durationScore * 0.5f + qualityScore * 0.5f));
    }

    // ═══════════════════════════════════════════════════════════════
    // 环形缓冲
    // ═══════════════════════════════════════════════════════════════
    private static class FloatRingBuffer {
        private final float[] data;
        private int head;
        private int count;

        FloatRingBuffer(int capacity) { data = new float[capacity]; }

        void add(float value) {
            data[head] = value;
            head = (head + 1) % data.length;
            if (count < data.length) count++;
        }

        int size() { return count; }

        float[] toArray() {
            float[] result = new float[count];
            if (count == 0) return result;
            int start = count < data.length ? 0 : head;
            for (int i = 0; i < count; i++) result[i] = data[(start + i) % data.length];
            return result;
        }

        void clear() { head = 0; count = 0; Arrays.fill(data, 0); }
    }

    private static class LongRingBuffer {
        private final long[] data;
        private int head;
        private int count;

        LongRingBuffer(int capacity) { data = new long[capacity]; }

        void add(long value) {
            data[head] = value;
            head = (head + 1) % data.length;
            if (count < data.length) count++;
        }

        int size() { return count; }

        long oldest() { return count > 0 ? data[startIndex()] : 0; }

        long newest() { return count > 0 ? data[newestIndex()] : 0; }

        long[] toArray() {
            long[] result = new long[count];
            if (count == 0) return result;
            int start = startIndex();
            for (int i = 0; i < count; i++) result[i] = data[(start + i) % data.length];
            return result;
        }

        void clear() { head = 0; count = 0; Arrays.fill(data, 0); }

        private int startIndex() { return count < data.length ? 0 : head; }
        private int newestIndex() {
            if (count == 0) return 0;
            return count < data.length ? count - 1 : (head == 0 ? data.length - 1 : head - 1);
        }
    }

    /** rPPG 分析结果 */
    public static class RppgResult {
        public final float bpm;
        public final float sdnn;
        public final float rmssd;
        public final float confidence;    // 0-1
        public final int signalQuality;   // 0-100
        public final String hint;         // 非空表示需要提示用户

        RppgResult(float bpm, float sdnn, float rmssd, float confidence,
                   int signalQuality, String hint) {
            this.bpm = bpm;
            this.sdnn = sdnn;
            this.rmssd = rmssd;
            this.confidence = confidence;
            this.signalQuality = signalQuality;
            this.hint = hint;
        }

        public boolean hasBpm() { return bpm > 0; }

        public boolean hasHrv() { return sdnn > 0 && rmssd > 0; }

        public String bpmText() {
            return hasBpm() ? String.format("%.0f", bpm) : "--";
        }

        public String confidenceText() {
            return String.format("%.0f%%", confidence * 100);
        }
    }
}
