package com.example.emoscope.history;

/** Keeps the record call-to-action limited to the genuine empty-history state. */
public final class HistoryEmptyStatePolicy {

    private HistoryEmptyStatePolicy() {
    }

    public static boolean shouldShowRecordAction(boolean historyEmpty) {
        return historyEmpty;
    }
}
