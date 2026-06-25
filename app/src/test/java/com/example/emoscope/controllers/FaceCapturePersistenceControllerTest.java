package com.example.emoscope.controllers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FaceCapturePersistenceControllerTest {

    @Test
    public void savesTheRecordBeforeNotifyingTheHost() {
        RecordingStore store = new RecordingStore();
        RecordingHost host = new RecordingHost(store);
        FaceCapturePersistenceController controller = new FaceCapturePersistenceController(
                Runnable::run, store, host);
        FaceCaptureRecord record = FaceCaptureRecord.create(72, "平静 70%", "开心 20%", "疲惫 10%");

        controller.save(record);

        assertEquals("面容分析", store.type);
        assertEquals(record.detail, store.detail);
        assertEquals(1, host.savedCount);
        assertEquals(record, host.lastSavedRecord);
        assertEquals(1, host.storeWriteCountWhenNotified);
    }

    private static final class RecordingStore implements FaceCapturePersistenceController.RecordStore {
        String type;
        String detail;
        boolean positive;
        int writeCount;

        @Override
        public void save(String type, String detail, boolean positive) {
            this.type = type;
            this.detail = detail;
            this.positive = positive;
            writeCount++;
        }
    }

    private static final class RecordingHost implements FaceCapturePersistenceController.Host {
        private final RecordingStore store;
        int savedCount;
        int storeWriteCountWhenNotified;
        FaceCaptureRecord lastSavedRecord;

        RecordingHost(RecordingStore store) {
            this.store = store;
        }

        @Override
        public void onSaved(FaceCaptureRecord record) {
            savedCount++;
            lastSavedRecord = record;
            storeWriteCountWhenNotified = store.writeCount;
        }
    }
}
