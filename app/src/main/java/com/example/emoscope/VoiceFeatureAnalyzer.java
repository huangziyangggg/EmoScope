package com.example.emoscope;

import java.util.Locale;

public final class VoiceFeatureAnalyzer {
    private VoiceFeatureAnalyzer() {
    }

    public enum Pace { SLOW, NORMAL, FAST }

    public enum Energy { LOW, NORMAL, HIGH }

    public static class Result {
        public final float charsPerSecond;
        public final float averageRms;
        public final float peakRms;
        public final int rmsSamples;
        public final Pace pace;
        public final Energy energy;
        public final String summary;

        Result(float charsPerSecond, float averageRms, float peakRms, int rmsSamples,
               Pace pace, Energy energy, String summary) {
            this.charsPerSecond = charsPerSecond;
            this.averageRms = averageRms;
            this.peakRms = peakRms;
            this.rmsSamples = rmsSamples;
            this.pace = pace;
            this.energy = energy;
            this.summary = summary;
        }
    }

    public static Result analyze(String text, float durationSec, float averageRms,
                                 float peakRms, int rmsSamples) {
        int charCount = text == null ? 0 : text.replaceAll("\\s+", "").length();
        float safeDuration = Math.max(0.5f, durationSec);
        float cps = charCount / safeDuration;

        Pace pace;
        if (cps >= 4.0f) {
            pace = Pace.FAST;
        } else if (cps < 1.5f) {
            pace = Pace.SLOW;
        } else {
            pace = Pace.NORMAL;
        }

        Energy energy;
        if (averageRms >= 6.5f || peakRms >= 9.5f) {
            energy = Energy.HIGH;
        } else if (averageRms > 0f && averageRms <= 2.0f) {
            energy = Energy.LOW;
        } else {
            energy = Energy.NORMAL;
        }

        String paceText = pace == Pace.FAST ? "语速偏快"
                : pace == Pace.SLOW ? "语速偏慢" : "语速平稳";
        String energyText = energy == Energy.HIGH ? "音量偏高"
                : energy == Energy.LOW ? "音量偏低" : "音量平稳";
        String summary = String.format(Locale.getDefault(), "%.1f字/秒，%s，%s",
                cps, paceText, energyText);

        return new Result(cps, averageRms, peakRms, rmsSamples, pace, energy, summary);
    }

    /**
     * 生成用户可读的温和解释，不含诊断性判断。
     * 语速和音量只是辅助观察线索，不等同于对情绪状态的判断。
     */
    public static String gentleDescription(Result result) {
        if (result == null) {
            return "暂时没有语音数据";
        }

        StringBuilder sb = new StringBuilder();

        switch (result.pace) {
            case FAST:
                sb.append("你说话比平时快了一些，可能心里有很多想说的。");
                break;
            case SLOW:
                sb.append("你的语速比较慢，听起来像是在认真照顾每一个字。");
                break;
            default:
                sb.append("你的语速很平稳，听起来从容而清晰。");
                break;
        }

        sb.append("\n");

        switch (result.energy) {
            case HIGH:
                sb.append("声音比较响亮，像是有比较强的感受想表达。");
                break;
            case LOW:
                sb.append("声音轻轻的，像在和自己对话。没有关系，有时候轻声说出来就很好。");
                break;
            default:
                sb.append("音量适中，听起来很自然。");
                break;
        }

        sb.append("\n\n提醒：这只是语音的辅助观察，不是对你状态的判断。");
        return sb.toString();
    }
}
