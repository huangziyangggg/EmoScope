package com.example.emoscope.history;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class HistoryBackupFormatterTest {
    @Test
    public void buildsCompatibleJsonAndEscapesDetailText() {
        HistoryBackupFormatter.Record record = new HistoryBackupFormatter.Record(
                "2026-06-20 09:00", "manual", "said \"ok\"\nnext", 1);

        String json = HistoryBackupFormatter.buildJson(Collections.singletonList(record));

        assertEquals("[{\"time\":\"2026-06-20 09:00\",\"type\":\"manual\","
                + "\"detail\":\"said \\\"ok\\\"\\nnext\",\"positive\":1}]", json);
    }
}
