package com.example.emoscope;

import java.util.ArrayList;
import java.util.List;

public final class EmotionConfidenceEvaluator {
    private EmotionConfidenceEvaluator() {}

    public static class Result {
        public final boolean isReliable;
        public final String message;

        Result(boolean isReliable, String message) {
            this.isReliable = isReliable;
            this.message = message;
        }
    }

    public static Result evaluate(float[] percentages, int luminance, boolean hasFace) {
        if (!hasFace) return new Result(false, "未检测到清晰人脸，结果仅供参考");

        float top = 0f;
        float second = 0f;
        for (float value : percentages) {
            if (value > top) {
                second = top;
                top = value;
            } else if (value > second) {
                second = value;
            }
        }

        List<String> cautions = new ArrayList<>();
        if (luminance < Constants.LUMINANCE_LOW) {
            cautions.add("光线偏暗");
        } else if (luminance > Constants.LUMINANCE_HIGH) {
            cautions.add("光线偏强");
        }
        if (top < 35f || top - second < 8f) {
            cautions.add("情绪信号接近");
        }

        if (cautions.isEmpty()) {
            return new Result(true, "可信度较稳定");
        }
        return new Result(false, String.join("，", cautions) + "，请结合自我感受判断");
    }
}
