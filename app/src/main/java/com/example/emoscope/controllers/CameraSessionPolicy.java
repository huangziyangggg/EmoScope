package com.example.emoscope.controllers;

/** Decides whether a camera session may start for a face-analysis request. */
public final class CameraSessionPolicy {

    private CameraSessionPolicy() {
    }

    public static boolean shouldStart(boolean cameraPermissionGranted,
                                      boolean faceAnalysisRequested) {
        return cameraPermissionGranted && faceAnalysisRequested;
    }
}
