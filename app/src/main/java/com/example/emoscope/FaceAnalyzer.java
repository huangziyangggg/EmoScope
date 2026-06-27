package com.example.emoscope;

import android.util.Log;

import com.google.mediapipe.tasks.components.containers.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 面容情绪分析引擎 — 52 个 MediaPipe blendshape → 10 种情绪 → 加权打分。
 * 积极/消极分类 + EMA 平滑 + Top3 排序显示。
 */
public class FaceAnalyzer {

    public interface FaceCallback {
        void onEmotionResult(EmotionResult result);
        void onNoFace();
    }

    public static class EmotionResult {
        public final String prob1, prob2, prob3;
        public final String cameraState;
        public final String moodLabel;
        public final int weightedScore;       // 0-100 加权情绪分
        public final boolean isWarning;
        public final String confidenceMessage;
        public final boolean isReliable;

        EmotionResult(String pb1, String pb2, String pb3, String cs,
                      String ml, int score, boolean warn,
                      String confidenceMessage, boolean isReliable) {
            prob1 = pb1; prob2 = pb2; prob3 = pb3;
            cameraState = cs; moodLabel = ml;
            weightedScore = score; isWarning = warn;
            this.confidenceMessage = confidenceMessage;
            this.isReliable = isReliable;
        }
    }

    // ── 10 种情绪定义 ─────────────────────────────────────────
    private static class EmotionDef {
        final String name;
        final int valence;     // 1=积极, 0=中性, -1=消极
        final int weight;      // 加权分数用: 积极高权, 消极低权

        EmotionDef(String n, int v, int w) { name = n; valence = v; weight = w; }
    }

    private static final EmotionDef[] EMOTIONS = {
        new EmotionDef("愉悦",   1, 95),   // 0
        new EmotionDef("平静",   1, 72),   // 1
        new EmotionDef("惊讶",   0, 50),   // 2
        new EmotionDef("轻蔑",   0, 45),   // 3
        new EmotionDef("悲伤",  -1, 28),   // 4
        new EmotionDef("焦虑",  -1, 22),   // 5
        new EmotionDef("愤怒",  -1, 15),   // 6
        new EmotionDef("恐惧",  -1, 10),   // 7
        new EmotionDef("厌恶",  -1, 8),    // 8
        new EmotionDef("疲惫",  -1, 35),   // 9
    };

    // ── 每种情绪的 blendshape 权重配置 ──────────────────────────
    // {blendshapeName, multiplier} — multiplier 已在 static 块预解析为 float
    private static final String[][][] EMOTION_BLENDSHAPES_RAW = {
        // 0: 愉悦 — 嘴角上扬 + 脸颊提起 + 眼角笑纹
        {{"mouthSmileLeft","1.6"},{"mouthSmileRight","1.6"},{"cheekSquintLeft","0.8"},{"cheekSquintRight","0.8"},{"eyeSquintLeft","0.4"},{"eyeSquintRight","0.4"}},
        // 1: 平静 — 低激活 = 1 - (其他激活总和)
        {},
        // 2: 惊讶 — 眉毛上扬 + 眼睛睁大 + 下巴张开
        {{"browOuterUpLeft","1.2"},{"browOuterUpRight","1.2"},{"eyeWideLeft","1.0"},{"eyeWideRight","1.0"},{"jawOpen","0.9"}},
        // 3: 轻蔑 — 单侧嘴角上扬 + 鼻翼
        {{"mouthDimpleLeft","1.5"},{"mouthDimpleRight","1.5"},{"mouthUpperUpLeft","0.6"},{"mouthUpperUpRight","0.6"}},
        // 4: 悲伤 — 嘴角下撇 + 眉毛内扬 + 眼睛下垂
        {{"mouthFrownLeft","1.4"},{"mouthFrownRight","1.4"},{"browInnerUp","1.0"},{"eyeLookDownLeft","0.5"},{"eyeLookDownRight","0.5"}},
        // 5: 焦虑 — 眼睑紧张 + 嘴唇按压 + 眉毛内扬
        {{"eyeSquintLeft","0.9"},{"eyeSquintRight","0.9"},{"mouthPressLeft","0.8"},{"mouthPressRight","0.8"},{"browInnerUp","0.6"},{"mouthStretchLeft","0.5"},{"mouthStretchRight","0.5"}},
        // 6: 愤怒 — 眉毛下压 + 嘴唇紧闭 + 鼻翼扩张
        {{"browDownLeft","1.5"},{"browDownRight","1.5"},{"mouthPressLeft","0.8"},{"mouthPressRight","0.8"},{"noseSneerLeft","0.7"},{"noseSneerRight","0.7"},{"mouthFrownLeft","0.5"},{"mouthFrownRight","0.5"}},
        // 7: 恐惧 — 眼睛睁大 + 眉毛上扬 + 嘴唇拉伸
        {{"eyeWideLeft","1.3"},{"eyeWideRight","1.3"},{"browInnerUp","0.9"},{"mouthStretchLeft","0.8"},{"mouthStretchRight","0.8"},{"jawOpen","0.5"}},
        // 8: 厌恶 — 鼻翼皱起 + 上唇抬起 + 眉毛下压
        {{"noseSneerLeft","1.6"},{"noseSneerRight","1.6"},{"mouthShrugUpper","0.9"},{"browDownLeft","0.5"},{"browDownRight","0.5"},{"mouthFrownLeft","0.4"},{"mouthFrownRight","0.4"}},
        // 9: 疲惫 — 眨眼频率 + 下巴前伸 + 眼睛向下
        {{"eyeBlinkLeft","1.4"},{"eyeBlinkRight","1.4"},{"jawForward","0.6"},{"eyeLookDownLeft","0.5"},{"eyeLookDownRight","0.5"},{"mouthShrugLower","0.4"}},
    };

    // 预解析权重，避免每帧重复 Float.parseFloat
    private static final float[][] EMOTION_BLENDSHAPE_WEIGHTS = new float[10][];
    static {
        for (int i = 0; i < 10; i++) {
            EMOTION_BLENDSHAPE_WEIGHTS[i] = new float[EMOTION_BLENDSHAPES_RAW[i].length];
            for (int j = 0; j < EMOTION_BLENDSHAPES_RAW[i].length; j++) {
                EMOTION_BLENDSHAPE_WEIGHTS[i][j] = Float.parseFloat(EMOTION_BLENDSHAPES_RAW[i][j][1]);
            }
        }
    }
    private static String blendshapeName(int emotionIdx, int j) {
        return EMOTION_BLENDSHAPES_RAW[emotionIdx][j][0];
    }

    private final float[] smoothedScores = new float[10];
    private long lastUiUpdate = 0;
    private final FaceCallback callback;
    private EmotionCalibrationProfile calibrationProfile = EmotionCalibrationProfile.none();
    private int ambientLuminance = 128;
    // 自适应平静基线 — 基于用户历史平均激活水平的 EWMA
    private float adaptiveCalmBaseline = 1.2f;
    private long totalFramesAnalyzed = 0;

    public FaceAnalyzer(FaceCallback callback) {
        this.callback = callback;
    }

    public void setCalibrationProfile(EmotionCalibrationProfile calibrationProfile) {
        this.calibrationProfile = calibrationProfile == null
                ? EmotionCalibrationProfile.none() : calibrationProfile;
    }

    public void setAmbientLuminance(int ambientLuminance) {
        this.ambientLuminance = ambientLuminance;
    }

    /**
     * 处理 FaceLandmarker 结果 — 52 blendshape → 10 情绪 → EMA → Top3
     */
    public void analyze(List<List<Category>> faceBlendshapesOrNull, long timestampMs) {
        if (faceBlendshapesOrNull == null || faceBlendshapesOrNull.isEmpty()) {
            callback.onNoFace();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastUiUpdate < Constants.FACE_UPDATE_INTERVAL_MS) return;
        lastUiUpdate = now;

        try {
            List<Category> shapes = faceBlendshapesOrNull.get(0);

            // ── 计算 10 种情绪的原始分 ──
            float[] rawScores = new float[10];
            float totalActivation = 0f;

            for (int i = 0; i < 10; i++) {
                if (EMOTION_BLENDSHAPES_RAW[i].length == 0) continue; // 平静单独算
                float score = 0f;
                for (int j = 0; j < EMOTION_BLENDSHAPES_RAW[i].length; j++) {
                    float val = muscle(shapes, blendshapeName(i, j)) * EMOTION_BLENDSHAPE_WEIGHTS[i][j];
                    score += val;
                }
                rawScores[i] = Math.min(1.5f, score); // 上限裁剪
                totalActivation += rawScores[i];
            }

            // 自适应平静基线 — 基于历史激活水平的 EWMA 调整
            totalFramesAnalyzed++;
            float activationTarget = (totalFramesAnalyzed < 50) ? 1.2f
                    : 1.0f + totalActivation / 9.0f * 1.8f; // 随用户激活水平自适应
            adaptiveCalmBaseline = 0.95f * adaptiveCalmBaseline + 0.05f * activationTarget;
            rawScores[1] = Math.max(0.05f, adaptiveCalmBaseline - totalActivation);

            // ── EMA 平滑 ──
            float alpha = Constants.FACE_EMA_ALPHA;
            for (int i = 0; i < 10; i++) {
                smoothedScores[i] = alpha * rawScores[i] + (1 - alpha) * smoothedScores[i];
            }

            // ── 归一化为百分比 ──
            float sum = 0f;
            for (float s : smoothedScores) sum += s;
            if (sum == 0) sum = 1;
            float[] percentages = new float[10];
            for (int i = 0; i < 10; i++) percentages[i] = smoothedScores[i] / sum * 100f;
            percentages = calibrationProfile.apply(percentages);

            // ── 加权打分 (0-100) ──
            float weightedSum = 0f, weightTotal = 0f;
            for (int i = 0; i < 10; i++) {
                float pct = percentages[i] / 100f;
                weightedSum += EMOTIONS[i].weight * pct;
                weightTotal += pct;
            }
            int finalScore = Math.round(weightedSum / Math.max(0.01f, weightTotal));
            finalScore = Math.max(5, Math.min(95, finalScore));

            // ── 排序取 Top3 ──
            List<int[]> ranked = new ArrayList<>();
            for (int i = 0; i < 10; i++) ranked.add(new int[]{i, Math.round(percentages[i])});
            Collections.sort(ranked, (a, b) -> b[1] - a[1]);

            int[] t1 = ranked.get(0), t2 = ranked.get(1), t3 = ranked.get(2);
            String prob1 = EMOTIONS[t1[0]].name + " " + t1[1] + "%";
            String prob2 = EMOTIONS[t2[0]].name + " " + t2[1] + "%";
            String prob3 = EMOTIONS[t3[0]].name + " " + t3[1] + "%";
            String cameraState = prob1 + " | " + prob2 + " | " + prob3;
            String moodLabel = "情绪指数 " + finalScore + " · " + EMOTIONS[t1[0]].name + "主导";
            if (calibrationProfile.isEnabled()) moodLabel += " · 已校准";
            EmotionConfidenceEvaluator.Result confidence =
                    EmotionConfidenceEvaluator.evaluate(percentages, ambientLuminance, true);

            // 消极主导且占比高 → 关注
            boolean isWarning = EMOTIONS[t1[0]].valence < 0 && t1[1] > 35;

            callback.onEmotionResult(new EmotionResult(
                    prob1, prob2, prob3, cameraState, moodLabel, finalScore, isWarning,
                    confidence.message, confidence.isReliable));

        } catch (Exception e) {
            Log.e(Constants.TAG, "FaceAnalyzer error", e);
        }
    }

    private static float muscle(List<Category> shapes, String name) {
        for (Category c : shapes) {
            if (c.categoryName().equals(name)) return c.score();
        }
        return 0f;
    }
}
