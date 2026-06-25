package com.example.emoscope;

/**
 * Maps bottom navigation selections to stable screen destinations without Android dependencies.
 */
public final class MainNavigationPolicy {

    public enum Destination {
        HOME,
        GROWTH,
        HISTORY,
        SETTINGS
    }

    private MainNavigationPolicy() {
    }

    public static Destination destinationFor(int selectedItemId, int homeItemId,
                                             int growthItemId, int historyItemId,
                                             int settingsItemId) {
        if (selectedItemId == homeItemId) {
            return Destination.HOME;
        }
        if (selectedItemId == growthItemId) {
            return Destination.GROWTH;
        }
        if (selectedItemId == historyItemId) {
            return Destination.HISTORY;
        }
        return Destination.SETTINGS;
    }
}
