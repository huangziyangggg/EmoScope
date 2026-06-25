package com.example.emoscope;

/**
 * Pure decision table for a single Android runtime permission request.
 */
public final class RuntimePermissionPolicy {

    public enum NextAction {
        ALREADY_GRANTED,
        SHOW_RATIONALE,
        REQUEST_SYSTEM_PERMISSION
    }

    private RuntimePermissionPolicy() {
    }

    public static NextAction nextAction(boolean granted, boolean shouldShowRationale) {
        if (granted) {
            return NextAction.ALREADY_GRANTED;
        }
        return shouldShowRationale ? NextAction.SHOW_RATIONALE
                : NextAction.REQUEST_SYSTEM_PERMISSION;
    }
}
