package com.example.emoscope.history;

import java.util.List;

/** Builds the existing JSON backup schema without Android dependencies. */
public final class HistoryBackupFormatter {
    private HistoryBackupFormatter() {
    }

    public static String buildJson(List<Record> records) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < records.size(); index++) {
            Record record = records.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"time\":\"").append(escape(record.time))
                    .append("\",\"type\":\"").append(escape(record.type))
                    .append("\",\"detail\":\"").append(escape(record.detail))
                    .append("\",\"positive\":").append(record.positive).append('}');
        }
        return json.append(']').toString();
    }

    private static String escape(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static final class Record {
        public final String time;
        public final String type;
        public final String detail;
        public final int positive;

        public Record(String time, String type, String detail, int positive) {
            this.time = time;
            this.type = type;
            this.detail = detail;
            this.positive = positive;
        }
    }
}
