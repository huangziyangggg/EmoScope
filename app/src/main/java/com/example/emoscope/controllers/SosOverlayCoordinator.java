package com.example.emoscope.controllers;

import android.view.View;

/**
 * Coordinates the direct actions exposed by the SOS breathing overlay.
 *
 * <p>The controller deliberately owns only UI event routing. Countdown, breathing and
 * user-confirmed SMS behavior remain in {@code SosInterventionController}.</p>
 */
public final class SosOverlayCoordinator {

    public interface Host {
        void performActionHaptic(View source);

        void stopBreathingIntervention();

        void dialHotline();
    }

    private final Host host;

    public SosOverlayCoordinator(Host host) {
        this.host = host;
    }

    public void bind(View closeView, View hotlineView) {
        closeView.setOnClickListener(this::onCloseClicked);
        hotlineView.setOnClickListener(this::onHotlineClicked);
    }

    public void onCloseClicked(View source) {
        host.performActionHaptic(source);
        host.stopBreathingIntervention();
    }

    public void onHotlineClicked(View source) {
        host.performActionHaptic(source);
        host.dialHotline();
    }
}
