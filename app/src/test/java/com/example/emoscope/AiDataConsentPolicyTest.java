package com.example.emoscope;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiDataConsentPolicyTest {
    @Test
    public void requiresConsentWhenConfiguredKeyHasNotBeenAcknowledged() {
        assertTrue(AiDataConsentPolicy.requiresConsent("sk-test", false));
        assertFalse(AiDataConsentPolicy.canSendToExternalAi("sk-test", false));
    }

    @Test
    public void allowsExternalAiOnlyAfterExplicitAcknowledgement() {
        assertFalse(AiDataConsentPolicy.requiresConsent("sk-test", true));
        assertTrue(AiDataConsentPolicy.canSendToExternalAi("sk-test", true));
    }

    @Test
    public void doesNotRequestConsentWithoutAnApiKey() {
        assertFalse(AiDataConsentPolicy.requiresConsent("   ", false));
        assertFalse(AiDataConsentPolicy.canSendToExternalAi("", true));
    }
}
