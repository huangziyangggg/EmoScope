package com.example.emoscope;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersonalProfileTest {
    @Test
    public void normalizesBlankValuesToPrivateDefaults() {
        PersonalProfile profile = new PersonalProfile("  ", null, "", "");

        assertEquals("我", profile.displayName());
        assertEquals("私人情绪档案", profile.identityLabel());
        assertEquals(Constants.DEFAULT_FOCUS_GOAL, profile.focusGoal());
        assertFalse(profile.hasCustomName());
    }

    @Test
    public void trimsUserVisibleFields() {
        PersonalProfile profile = new PersonalProfile("  Ziyang  ", "  学生  ", " 减压 ", " calm ");

        assertEquals("Ziyang", profile.displayName());
        assertEquals("学生", profile.identityLabel());
        assertEquals("减压", profile.focusGoal());
        assertEquals("calm", profile.emotionPreference());
        assertTrue(profile.hasCustomName());
    }

    @Test
    public void buildsPrivacyFirstSummary() {
        PersonalProfile profile = new PersonalProfile("Ziyang", "学生", "减压", "平静");

        assertEquals("Ziyang · 学生 · 关注减压 · 本机保存", profile.summary());
    }
}
