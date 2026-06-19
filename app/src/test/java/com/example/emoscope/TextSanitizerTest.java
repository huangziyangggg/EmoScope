package com.example.emoscope;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TextSanitizerTest {
    @Test
    public void collapsesWhitespaceAndTrims() {
        assertEquals("Ziyang Huang", TextSanitizer.cleanSingleLine("  Ziyang   Huang \n "));
    }

    @Test
    public void limitsLengthWithoutBreakingShortText() {
        assertEquals("abcdef", TextSanitizer.limit("abcdef", 10));
    }

    @Test
    public void limitsLongTextWithEllipsis() {
        assertEquals("abcd...", TextSanitizer.limit("abcdefghij", 7));
    }

    @Test
    public void safeLabelUsesFallbackWhenBlank() {
        assertEquals("私人情绪档案", TextSanitizer.safeLabel("   ", "私人情绪档案", 12));
    }
}
