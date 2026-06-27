package com.example.emoscope;

public class PersonalProfile {
    private static final String DEFAULT_DISPLAY_NAME = "我";
    private static final String DEFAULT_IDENTITY_LABEL = "私人情绪档案";

    private final String displayName;
    private final String identityLabel;
    private final String focusGoal;
    private final String emotionPreference;

    public PersonalProfile(String displayName, String identityLabel,
                           String focusGoal, String emotionPreference) {
        this.displayName = TextSanitizer.safeLabel(displayName, DEFAULT_DISPLAY_NAME, 18);
        this.identityLabel = TextSanitizer.safeLabel(identityLabel, DEFAULT_IDENTITY_LABEL, 18);
        this.focusGoal = TextSanitizer.safeLabel(focusGoal, Constants.DEFAULT_FOCUS_GOAL, 18);
        this.emotionPreference = TextSanitizer.safeLabel(emotionPreference, "", 18);
    }

    public String displayName() {
        return displayName;
    }

    public String identityLabel() {
        return identityLabel;
    }

    public String focusGoal() {
        return focusGoal;
    }

    public String emotionPreference() {
        return emotionPreference;
    }

    public boolean hasCustomName() {
        return !DEFAULT_DISPLAY_NAME.equals(displayName);
    }

    public String summary() {
        return displayName + " · " + identityLabel + " · 关注" + focusGoal + " · 本机保存";
    }
}
