package com.example.emoscope;

/** Product promise shown before a user creates their first record. */
public final class OnboardingNarrative {

    private OnboardingNarrative() {
    }

    public static String title() {
        return "欢迎来到" + AppBrand.APP_NAME;
    }

    public static String message() {
        return "这里不是一张给情绪打分的表。\n\n"
                + AppBrand.APP_NAME + "愿意陪你，把说不清的时刻慢慢看清。\n\n"
                + "安顿此刻\n"
                + "用手写、语音或面容分析，留下一点真实感受。\n\n"
                + "看见线索\n"
                + "记录会在本机积累；有足够真实记录时，才出现属于你的回看。\n\n"
                + "走自己的路\n"
                + "成长不是打卡和升级。你可以按自己的速度，选择日记、感恩或呼吸练习。\n\n"
                + "你的记录优先留在本机。使用 AI 解读前，我们会征得你的明确同意。\n\n"
                + AppBrand.APP_NAME + "不提供医疗诊断，也不能替代专业心理支持或紧急救援。";
    }

    public static String primaryActionLabel() {
        return "从此刻开始";
    }
}
