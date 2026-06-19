package com.example.emoscope;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SignalUpdateGateTest {
    @Test
    public void firstUpdateIsAllowed() {
        SignalUpdateGate gate = new SignalUpdateGate(500, 8);

        assertTrue(gate.shouldUpdate(1000, 120));
    }

    @Test
    public void smallChangeInsideIntervalIsSuppressed() {
        SignalUpdateGate gate = new SignalUpdateGate(500, 8);

        gate.shouldUpdate(1000, 120);

        assertFalse(gate.shouldUpdate(1200, 124));
    }

    @Test
    public void largeChangeInsideIntervalIsAllowed() {
        SignalUpdateGate gate = new SignalUpdateGate(500, 8);

        gate.shouldUpdate(1000, 120);

        assertTrue(gate.shouldUpdate(1200, 135));
    }

    @Test
    public void staleSignalIsAllowedEvenWhenValueIsSimilar() {
        SignalUpdateGate gate = new SignalUpdateGate(500, 8);

        gate.shouldUpdate(1000, 120);

        assertTrue(gate.shouldUpdate(1600, 122));
    }
}
