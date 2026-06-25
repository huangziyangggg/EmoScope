package com.example.emoscope.controllers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SosOverlayCoordinatorTest {

    @Test
    public void closeActionHapticsAndStopsOnlyTheBreathingIntervention() {
        RecordingHost host = new RecordingHost();
        SosOverlayCoordinator coordinator = new SosOverlayCoordinator(host);

        coordinator.onCloseClicked(null);

        assertEquals(1, host.hapticCount);
        assertEquals(1, host.stopCount);
        assertEquals(0, host.hotlineCount);
    }

    @Test
    public void hotlineActionHapticsAndDelegatesDialingToHost() {
        RecordingHost host = new RecordingHost();
        SosOverlayCoordinator coordinator = new SosOverlayCoordinator(host);

        coordinator.onHotlineClicked(null);

        assertEquals(1, host.hapticCount);
        assertEquals(0, host.stopCount);
        assertEquals(1, host.hotlineCount);
    }

    private static final class RecordingHost implements SosOverlayCoordinator.Host {
        int hapticCount;
        int stopCount;
        int hotlineCount;

        @Override public void performActionHaptic(android.view.View source) { hapticCount++; }
        @Override public void stopBreathingIntervention() { stopCount++; }
        @Override public void dialHotline() { hotlineCount++; }
    }
}
