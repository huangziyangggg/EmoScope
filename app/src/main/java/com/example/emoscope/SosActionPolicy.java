package com.example.emoscope;

/** Keeps emergency messaging opt-in at the final SOS action. */
public final class SosActionPolicy {
    private SosActionPolicy() {
    }

    public static boolean shouldSendEmergencySms(boolean userExplicitlyRequestedSms) {
        return userExplicitlyRequestedSms;
    }
}
