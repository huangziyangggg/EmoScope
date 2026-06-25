package com.example.emoscope;

/**
 * 让底部导航在边到边模式下保留原有内容区，并把系统导航栏作为额外安全区域。
 */
public final class BottomNavigationInsetsPolicy {

    private BottomNavigationInsetsPolicy() {
    }

    public static int containerHeight(int baseHeight, int navigationBarInset) {
        return baseHeight + Math.max(0, navigationBarInset);
    }

    public static int bottomPadding(int basePadding, int navigationBarInset) {
        return basePadding + Math.max(0, navigationBarInset);
    }
}
