package com.example.emoscope;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BottomNavigationInsetsPolicyTest {

    @Test
    public void keepsTheNavigationContentHeightWhenSystemBarIsPresent() {
        assertEquals(112, BottomNavigationInsetsPolicy.containerHeight(88, 24));
        assertEquals(30, BottomNavigationInsetsPolicy.bottomPadding(6, 24));
    }

    @Test
    public void ignoresInvalidNegativeInsets() {
        assertEquals(88, BottomNavigationInsetsPolicy.containerHeight(88, -1));
        assertEquals(6, BottomNavigationInsetsPolicy.bottomPadding(6, -1));
    }
}
