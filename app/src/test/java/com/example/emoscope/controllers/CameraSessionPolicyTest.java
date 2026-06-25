package com.example.emoscope.controllers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CameraSessionPolicyTest {

    @Test
    public void startsOnlyWhenTheUserRequestedFaceAnalysisAndPermissionIsGranted() {
        assertFalse(CameraSessionPolicy.shouldStart(false, false));
        assertFalse(CameraSessionPolicy.shouldStart(false, true));
        assertFalse(CameraSessionPolicy.shouldStart(true, false));
        assertTrue(CameraSessionPolicy.shouldStart(true, true));
    }
}
