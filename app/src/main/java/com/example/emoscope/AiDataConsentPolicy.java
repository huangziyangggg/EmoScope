package com.example.emoscope;

/** Defines when user content may be sent to an external AI service. */
public final class AiDataConsentPolicy {
    private AiDataConsentPolicy() {
    }

    public static boolean requiresConsent(String apiKey, boolean acknowledged) {
        return hasApiKey(apiKey) && !acknowledged;
    }

    public static boolean canSendToExternalAi(String apiKey, boolean acknowledged) {
        return hasApiKey(apiKey) && acknowledged;
    }

    private static boolean hasApiKey(String apiKey) {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
