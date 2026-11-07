package com.sidekey.lsp;

/** 模块 App 与 system_server 共用的配置键 */
public final class Cfg {
    public static final String PKG = "com.sidekey.lsp";
    public static final String PREFS = "config";

    public static final String SCANCODE = "scancode";       // int，Ace 6 快捷键 = 735
    public static final String SWALLOW = "swallow";         // 拦截系统原功能
    public static final String HAPTIC = "haptic";
    public static final String DOUBLE_MS = "double_ms";
    public static final String LONG_MS = "long_ms";
    public static final String MEDIA_TARGET = "media_target"; // 指定播放器包名，空 = 当前播放器
    public static final String ACT_PREFIX = "act_";          // act_single / act_double / act_long

    public static final int DEF_SCANCODE = 735;
    public static final int DEF_DOUBLE_MS = 250;
    public static final int DEF_LONG_MS = 500;

    private Cfg() {}
}
