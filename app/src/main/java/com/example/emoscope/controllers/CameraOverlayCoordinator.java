package com.example.emoscope.controllers;

import android.view.View;

/** Routes direct interactions from the camera analysis overlay to its host. */
public final class CameraOverlayCoordinator {

    public interface Host {
        void performActionHaptic(View source);

        void closeCameraOverlay();

        void flipCamera(View source);

        void captureFaceScore();

        void saveCaptureResult();

        void discardCaptureResult();
    }

    private final Host host;

    public CameraOverlayCoordinator(Host host) {
        this.host = host;
    }

    public void bind(View closeView, View flipView, View captureView,
                     View saveView, View discardView) {
        closeView.setOnClickListener(this::onCloseClicked);
        flipView.setOnClickListener(this::onFlipClicked);
        captureView.setOnClickListener(this::onCaptureClicked);
        saveView.setOnClickListener(view -> onSaveClicked());
        discardView.setOnClickListener(view -> onDiscardClicked());
    }

    public void onCloseClicked(View source) {
        host.performActionHaptic(source);
        host.closeCameraOverlay();
    }

    public void onFlipClicked(View source) {
        host.performActionHaptic(source);
        host.flipCamera(source);
    }

    public void onCaptureClicked(View source) {
        host.performActionHaptic(source);
        host.captureFaceScore();
    }

    public void onSaveClicked() {
        host.saveCaptureResult();
    }

    public void onDiscardClicked() {
        host.discardCaptureResult();
    }
}
