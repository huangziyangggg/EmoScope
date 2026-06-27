package com.example.emoscope.history;

import com.example.emoscope.AppBrand;

import java.util.List;

public final class HistoryExportFormatter {
    private HistoryExportFormatter() {
    }

    public static final class Record {
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

        public static Record fromLegacyRow(String[] row) {
            if (row == null) {
                return new Record("", "", "", false);
            }
            return new Record(
                    valueAt(row, 0),
                    valueAt(row, 1),
                    valueAt(row, 2),
                    "1".equals(valueAt(row, 3))
            );
        }

        private static String valueAt(String[] row, int index) {
            return index < row.length && row[index] != null ? row[index] : "";
        }
    }

    public static String buildText(List<Record> rows, String generatedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(AppBrand.APP_NAME).append("情绪分析报告】\n");
        sb.append("生成时间：").append(generatedAt).append("\n\n");

        int count = 0;
        for (Record row : rows) {
            count++;
            sb.append("--- 样本 ").append(count).append(" ---\n")
                    .append("时刻: ").append(row.time).append("\n")
                    .append("类型: ").append(row.type).append("\n")
                    .append("判定: ").append(row.positive ? "[积极/平稳]" : "[压力/预警]").append("\n")
                    .append("详情:\n").append(row.detail).append("\n\n");
        }
        return sb.toString();
    }

    public static String buildCsv(List<Record> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("时间,类型,情绪判定,详情\n");
        for (Record row : rows) {
            sb.append(row.time).append(",")
                    .append(row.type).append(",")
                    .append(row.positive ? "积极" : "预警").append(",")
                    .append(csv(row.detail)).append("\n");
        }
        return sb.toString();
    }

    public static String buildMarkdown(List<Record> rows, String generatedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(AppBrand.APP_NAME).append("情绪分析报告\n\n");
        sb.append("> 生成时间: ").append(generatedAt);
        sb.append("\n\n| # | 时间 | 类型 | 情绪 | 详情 |\n");
        sb.append("|---|------|------|------|------|\n");

        int count = 0;
        for (Record row : rows) {
            count++;
            sb.append("| ").append(count)
                    .append(" | ").append(row.time)
                    .append(" | ").append(row.type)
                    .append(" | ").append(row.positive ? "[积极]" : "[预警]")
                    .append(" | ").append(shortDetail(row.detail)).append(" |\n");
        }
        sb.append("\n> 共 ").append(rows.size()).append(" 条记录\n");
        return sb.toString();
    }

    private static String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")
                .replace("\n", " / ")
                .replace("\r", "")) + "\"";
    }

    private static String shortDetail(String detail) {
        String normalized = detail == null ? "" : detail.replace("\n", " ");
        return normalized.length() > 60
                ? normalized.substring(0, 57) + "..."
                : normalized;
    }
}
