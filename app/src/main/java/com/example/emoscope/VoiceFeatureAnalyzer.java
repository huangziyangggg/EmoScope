package com.example.emoscope;

import java.util.Locale;

public final class VoiceFeatureAnalyzer {
    private VoiceFeatureAnalyzer() {}

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
        if (cps >= 4.0f) pace = Pace.FAST;
        else if (cps < 1.5f) pace = Pace.SLOW;
        else pace = Pace.NORMAL;

        Energy energy;
        if (averageRms >= 6.5f || peakRms >= 9.5f) energy = Energy.HIGH;
        else if (averageRms > 0f && averageRms <= 2.0f) energy = Energy.LOW;
        else energy = Energy.NORMAL;

        String paceText = pace == Pace.FAST ? "语速偏快"
                : pace == Pace.SLOW ? "语速偏慢" : "语速平稳";
        String energyText = energy == Energy.HIGH ? "音量偏高"
                : energy == Energy.LOW ? "音量偏低" : "音量平稳";
        String summary = String.format(Locale.getDefault(), "%.1f字/秒，%s，%s",
                cps, paceText, energyText);

        return new Result(cps, averageRms, peakRms, rmsSamples, pace, energy, summary);
    }
}
