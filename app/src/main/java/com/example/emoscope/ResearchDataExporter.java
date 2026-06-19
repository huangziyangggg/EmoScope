package com.example.emoscope;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ResearchDataExporter {
    private ResearchDataExporter() {}

    public static class Record {
        public final String time;
        public final String type;
        public final String detail;
        public final boolean positive;

        public Record(String time, String type, String detail, boolean positive) {
            this.time = time == null ? "" : time;
            this.type = type == null ? "" : type;
            this.detail = detail == null ? "" : detail;
            this.positive = positive;
        }
    }

    public static String buildAnonymousJson(List<Record> records, String source) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJsonField(sb, "schema", "emoscope-research-export-v1", true, 1);
        appendJsonField(sb, "source", source, true, 1);
        appendJsonField(sb, "created_at", new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()), true, 1);
        appendJsonField(sb, "method_note",
                "Anonymized engineering export for research review; not clinical diagnosis.", true, 1);
        sb.append("  \"record_count\":").append(records.size()).append(",\n");
        sb.append("  \"records\":[\n");
        for (int i = 0; i < records.size(); i++) {
            Record r = records.get(i);
            sb.append("    {");
            sb.append("\"index\":").append(i + 1).append(",");
            sb.append("\"time_bucket\":\"").append(escape(bucketTime(r.time))).append("\",");
            sb.append("\"type\":\"").append(escape(r.type)).append("\",");
            sb.append("\"valence\":\"").append(r.positive ? "positive_or_stable" : "warning_or_pressure").append("\",");
            sb.append("\"detail\":\"").append(escape(anonymize(r.detail))).append("\"");
            sb.append("}");
            if (i < records.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static String buildAnonymousCsv(List<Record> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFFindex,time_bucket,type,valence,detail\n");
        for (int i = 0; i < records.size(); i++) {
            Record r = records.get(i);
            sb.append(i + 1).append(",");
            sb.append(csv(bucketTime(r.time))).append(",");
            sb.append(csv(r.type)).append(",");
            sb.append(r.positive ? "positive_or_stable" : "warning_or_pressure").append(",");
            sb.append(csv(anonymize(r.detail))).append("\n");
        }
        return sb.toString();
    }

    public static String anonymize(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\b1[3-9]\\d{9}\\b", "[phone]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[email]")
                .replaceAll("\\b\\d{15,18}[0-9Xx]?\\b", "[id]");
    }

    private static String bucketTime(String time) {
        if (time == null || time.length() < 10) return "unknown";
        return time.substring(0, 10);
    }

    private static void appendJsonField(StringBuilder sb, String key, String value,
                                        boolean comma, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
        sb.append("\"").append(escape(key)).append("\":\"").append(escape(value)).append("\"");
        if (comma) sb.append(",");
        sb.append("\n");
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private static String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")
                .replace("\n", " / ").replace("\r", "")) + "\"";
    }
}
