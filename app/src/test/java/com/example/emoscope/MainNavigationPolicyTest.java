package com.example.emoscope;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MainNavigationPolicyTest {

    @Test
    public void mapsEachBottomNavigationItemToItsDestination() {
        assertEquals(MainNavigationPolicy.Destination.HOME,
                MainNavigationPolicy.destinationFor(10, 10, 20, 30, 40));
        assertEquals(MainNavigationPolicy.Destination.GROWTH,
                MainNavigationPolicy.destinationFor(20, 10, 20, 30, 40));
        assertEquals(MainNavigationPolicy.Destination.HISTORY,
                MainNavigationPolicy.destinationFor(30, 10, 20, 30, 40));
        assertEquals(MainNavigationPolicy.Destination.SETTINGS,
                MainNavigationPolicy.destinationFor(40, 10, 20, 30, 40));
    }

    @Test
    public void treatsAnUnknownDestinationAsSettingsForCompatibility() {
        assertEquals(MainNavigationPolicy.Destination.SETTINGS,
                MainNavigationPolicy.destinationFor(99, 10, 20, 30, 40));
    }
}
