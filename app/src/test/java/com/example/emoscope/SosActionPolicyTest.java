package com.example.emoscope;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SosActionPolicyTest {
    @Test
    public void sendsEmergencySmsOnlyWhenUserExplicitlyChoosesIt() {
        assertFalse(SosActionPolicy.shouldSendEmergencySms(false));
        assertTrue(SosActionPolicy.shouldSendEmergencySms(true));
    }
}
