package com.example.emoscope.controllers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CameraOverlayCoordinatorTest {

    @Test
    public void closeActionHapticsAndClosesOnlyTheCameraOverlay() {
        RecordingHost host = new RecordingHost();
        CameraOverlayCoordinator coordinator = new CameraOverlayCoordinator(host);

        coordinator.onCloseClicked(null);

        assertEquals(1, host.hapticCount);
        assertEquals(1, host.closeCount);
        assertEquals(0, host.flipCount);
        assertEquals(0, host.captureCount);
    }

    @Test
    public void flipActionHapticsAndDelegatesCameraFlip() {
        RecordingHost host = new RecordingHost();
        CameraOverlayCoordinator coordinator = new CameraOverlayCoordinator(host);

        coordinator.onFlipClicked(null);

        assertEquals(1, host.hapticCount);
        assertEquals(1, host.flipCount);
        assertEquals(0, host.captureCount);
    }

    @Test
    public void captureActionHapticsAndDelegatesCapture() {
        RecordingHost host = new RecordingHost();
        CameraOverlayCoordinator coordinator = new CameraOverlayCoordinator(host);

        coordinator.onCaptureClicked(null);

        assertEquals(1, host.hapticCount);
        assertEquals(1, host.captureCount);
        assertEquals(0, host.saveCount);
    }

    @Test
    public void saveAndDiscardActionsDoNotAddUnrelatedHaptics() {
        RecordingHost host = new RecordingHost();
        CameraOverlayCoordinator coordinator = new CameraOverlayCoordinator(host);

        coordinator.onSaveClicked();
        coordinator.onDiscardClicked();

        assertEquals(0, host.hapticCount);
        assertEquals(1, host.saveCount);
        assertEquals(1, host.discardCount);
    }

    private static final class RecordingHost implements CameraOverlayCoordinator.Host {
        int hapticCount;
        int closeCount;
        int flipCount;
        int captureCount;
        int saveCount;
        int discardCount;

        @Override public void performActionHaptic(android.view.View source) { hapticCount++; }
        @Override public void closeCameraOverlay() { closeCount++; }
        @Override public void flipCamera(android.view.View source) { flipCount++; }
        @Override public void captureFaceScore() { captureCount++; }
        @Override public void saveCaptureResult() { saveCount++; }
        @Override public void discardCaptureResult() { discardCount++; }
    }
}
