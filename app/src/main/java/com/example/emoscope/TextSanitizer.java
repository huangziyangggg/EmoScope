package com.example.emoscope;

public final class TextSanitizer {
    private TextSanitizer() {}

    public static String cleanSingleLine(String value) {
        if (value == null) return "";
        return value.replaceAll("\\s+", " ").trim();
    }

    public static String limit(String value, int maxChars) {
        String clean = cleanSingleLine(value);
        if (maxChars <= 0 || clean.length() <= maxChars) return clean;
        if (maxChars <= 3) return clean.substring(0, maxChars);
        return clean.substring(0, maxChars - 3) + "...";
    }

    public static String safeLabel(String value, String fallback, int maxChars) {
        String clean = limit(value, maxChars);
        return clean.isEmpty() ? fallback : clean;
    }
}
