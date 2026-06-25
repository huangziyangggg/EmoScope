package com.example.emoscope.controllers;

import java.util.concurrent.Executor;

/** Writes face snapshots off the UI thread and notifies the host after persistence succeeds. */
public final class FaceCapturePersistenceController {

    public interface RecordStore {
        void save(String type, String detail, boolean positive);
    }

    public interface Host {
        void onSaved(FaceCaptureRecord record);
    }

    private final Executor executor;
    private final RecordStore recordStore;
    private final Host host;

    public FaceCapturePersistenceController(Executor executor, RecordStore recordStore, Host host) {
        this.executor = executor;
        this.recordStore = recordStore;
        this.host = host;
    }

    public void save(FaceCaptureRecord record) {
        executor.execute(() -> {
            recordStore.save(record.type, record.detail, record.isPositive);
            host.onSaved(record);
        });
    }
}
