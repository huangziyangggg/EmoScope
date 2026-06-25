package com.example.emoscope;

/**
 * EmoScope 全局常量定义
 * 统一管理 SharedPreferences Key、数据库配置、网络参数等，消除魔法字符串。
 */
public final class Constants {

    private Constants() {
        // 工具类禁止实例化
    }

    // ── SharedPreferences ───────────────────────────────────────
    public static final String PREFS_NAME = "EmoScopePrefs";
    public static final String KEY_API_KEY = "apikey";
    public static final String KEY_AI_DATA_CONSENT = "ai_data_consent";
    public static final String KEY_CONTACT = "contact";
    public static final String KEY_TTS = "tts";
    public static final String KEY_HAPTIC = "haptic";
    public static final String KEY_SHAKE_THRESH = "shake_thresh";
    public static final String KEY_BIOMETRIC = "biometric_lock";
    public static final String KEY_BREATH_MODE = "breath_mode";

    // ── 默认值 ──────────────────────────────────────────────────
    /** DeepSeek API Key 默认值 — 空字符串，首次启动引导用户配置 */
    public static final String DEFAULT_API_KEY = "";
    /** 紧急联系人默认值 — 空字符串，引导用户在设置中填写 */
    public static final String DEFAULT_CONTACT = "";
    public static final boolean DEFAULT_TTS = true;
    public static final boolean DEFAULT_HAPTIC = true;
    public static final float DEFAULT_SHAKE_THRESHOLD = 2.5f;

    // ── 数据库 ──────────────────────────────────────────────────
    public static final String DB_NAME = "EmoDB.db";
    public static final int DB_VERSION = 1;
    public static final String TABLE_RECORDS = "records";
    public static final String COL_ID = "id";
    public static final String COL_TIME = "time";
    public static final String COL_TYPE = "type";
    public static final String COL_DETAIL = "detail";
    public static final String COL_POSITIVE = "positive";

    // ── 网络 / API ──────────────────────────────────────────────
    public static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";
    public static final String DEEPSEEK_MODEL = "deepseek-chat";
    /** 连接超时（秒） */
    public static final int CONNECT_TIMEOUT_SEC = 10;
    /** 读取超时（秒） — AI 推理需要一定时间 */
    public static final int READ_TIMEOUT_SEC = 20;
    /** AI 最大输出 token 数 */
    public static final int AI_MAX_TOKENS = 200;
    /** AI 生成温度 (0-2, 越高越随机) */
    public static final double AI_TEMPERATURE = 0.7;
    /** AI 重复惩罚系数 */
    public static final double AI_FREQUENCY_PENALTY = 0.3;
    /** AI 请求最大重试次数 */
    public static final int AI_MAX_RETRIES = 3;
    /** AI 重试基础延迟（毫秒），指数退避: delay * 2^attempt */
    public static final long AI_RETRY_BASE_DELAY_MS = 1000;

    // ── SOS / 安全 ──────────────────────────────────────────────
    public static final String HOTLINE_NUMBER = "12355";
    public static final long SHAKE_COOLDOWN_MS = 4000;
    /** 两次 SOS 触发之间最短间隔，防止重复发送短信 */
    public static final long SOS_SMS_COOLDOWN_MS = 60_000;

    // ── 模型路径 ────────────────────────────────────────────────
    public static final String FACELANDMARKER_MODEL = "face_landmarker.task";

    // ── 情绪检测（与 FaceAnalyzer 10 情绪对齐）──────────────
    public static final String[] EMOTION_NAMES = {
        "愉悦", "平静", "惊讶", "轻蔑", "悲伤", "焦虑", "愤怒", "恐惧", "厌恶", "疲惫"
    };
    public static final String[] EMOTION_EMOJIS = {
        "😆", "😐", "😲", "😏", "😭", "😟", "😠", "😱", "🤢", "🥱"
    };
    /** 主导情绪触发 SOS 提示的阈值 */
    public static final float SOS_EMOTION_THRESHOLD = 0.4f;
    /** EMA 平滑系数 (0-1), 越小越平滑但越滞后 */
    public static final float FACE_EMA_ALPHA = 0.3f;
    /** 面容 UI 更新最小间隔 (毫秒), 降低更新频率避免抖动 */
    public static final long FACE_UPDATE_INTERVAL_MS = 200;
    /** FaceLandmarker 推理最小间隔 (毫秒), 降低相机分析链路的 CPU/GPU 压力 */
    public static final long FACE_DETECTION_INTERVAL_MS = 250;
    /** 面容检测最小置信度, 低于此值保留上一帧 */
    public static final float MIN_FACE_CONFIDENCE = 0.5f;

    // ── 手动心情记录 ────────────────────────────────────────────
    public static final String KEY_STREAK_COUNT = "streak_count";
    public static final String KEY_LAST_RECORD_DATE = "last_record_date";
    public static final String[] MANUAL_MOOD_EMOJIS = {"😆","😊","😐","😴","😢","😰","😡","🙏"};
    public static final String[] MANUAL_MOOD_LABELS = {"开心","满足","平静","疲惫","难过","焦虑","愤怒","感恩"};
    public static final int[] MANUAL_MOOD_ICONS = {
        R.drawable.ic_mood_grin,    // 开心
        R.drawable.ic_mood_smile,   // 满足
        R.drawable.ic_mood_neutral, // 平静
        R.drawable.ic_mood_sleepy,  // 疲惫
        R.drawable.ic_mood_cry,     // 难过
        R.drawable.ic_mood_anxious, // 焦虑
        R.drawable.ic_mood_angry,   // 愤怒
        R.drawable.ic_mood_pray     // 感恩
    };

    // ── 语音交互 ────────────────────────────────────────────────
    /** true=点击切换模式, false=长按录音模式 */
    public static final String KEY_VOICE_CLICK_MODE = "voice_click_mode";
    public static final boolean DEFAULT_VOICE_CLICK_MODE = false;
    /** 点击模式自动停止秒数 */
    public static final int VOICE_AUTO_STOP_SEC = 30;

    // ── 隐私模式 ────────────────────────────────────────────────
    public static final String KEY_PRIVACY_MODE = "privacy_mode";
    public static final boolean DEFAULT_PRIVACY_MODE = false;
    public static final String KEY_EMOTION_CALIBRATION = "emotion_calibration_profile";
    public static final String KEY_MODEL_BOUNDARY_ACK = "model_boundary_ack";
    public static final String KEY_PROFILE_NAME = "profile_name";
    public static final String KEY_PROFILE_IDENTITY = "profile_identity";
    public static final String KEY_PROFILE_EMOTION_PREF = "profile_emotion_preference";

    // ── 通知提醒 ────────────────────────────────────────────────
    public static final String KEY_NOTIFY_DAILY = "notify_daily";
    public static final String KEY_NOTIFY_WEEKLY = "notify_weekly";
    public static final String KEY_NOTIFY_HOUR = "notify_hour";
    public static final String KEY_NOTIFY_MINUTE = "notify_minute";
    public static final int DEFAULT_NOTIFY_HOUR = 20;
    public static final int DEFAULT_NOTIFY_MINUTE = 0;

    // ── 情绪标签 ────────────────────────────────────────────────
    public static final String[] EMOTION_TAGS = {"工作", "家庭", "健康", "社交", "学习", "其他"};
    public static final String COL_TAG = "tag";

    // ── 呼吸模式 ────────────────────────────────────────────────
    /** 方块呼吸 (4-4-4-4): 吸气4秒-屏息4秒-呼气4秒-屏息4秒 */
    public static final int BREATH_MODE_BOX = 0;
    /** 4-7-8 放松: 吸气4秒-屏息7秒-呼气8秒 */
    public static final int BREATH_MODE_478 = 1;
    public static final String[] BREATH_MODE_NAMES = {"方块呼吸", "4-7-8 放松"};
    public static final String[] BREATH_MODE_DESCS = {"吸气4秒·屏息4秒·呼气4秒·屏息4秒", "吸气4秒·屏息7秒·呼气8秒"};
    // 每种模式的阶段时长数组 (毫秒): [吸气, 屏息, 呼气, 屏息]
    public static final long[][] BREATH_PHASES = {
        {4000, 4000, 4000, 4000},  // 方块呼吸
        {4000, 7000, 8000, 0},      // 4-7-8 (无末段屏息)
    };

    // ── 光照 ────────────────────────────────────────────────────
    /** 低光照阈值（0-255 亮度范围） */
    public static final int LUMINANCE_LOW = 60;
    /** 高光照阈值 */
    public static final int LUMINANCE_HIGH = 190;
    /** 亮度采样步长 — 每隔 N 个像素采样一次，平衡性能与精度 */
    public static final int LUMINANCE_SAMPLE_STEP = 10;

    // ── 成长等级 ────────────────────────────────────────────────
    public static final String[] LEVEL_NAMES = {
        "情绪观察者", "情绪记录者", "内心探索者", "情绪管理者", "自我掌控者"
    };
    public static final int[] LEVEL_THRESHOLDS = {0, 5, 20, 50, 100}; // 总记录数阈值
    public static final String KEY_USER_LEVEL = "user_level";

    // ── 日志 ────────────────────────────────────────────────────
    public static final String TAG = "EmoScope";

    // ── 权限请求码 ──────────────────────────────────────────────
    /** 核心权限（相机 + 麦克风）请求码 */
    /** SMS 权限请求码 */
    public static final int PERM_SMS = 101;
    /** 通知权限请求码 (Android 13+) */
    public static final int PERM_NOTIFY = 102;
    /** 独立麦克风权限请求码 */
    public static final int PERM_AUDIO = 103;
    public static final int PERM_CAMERA = 104;
    /** 首次启动引导 */
    public static final String KEY_FIRST_LAUNCH = "first_launch_done";
    /** 用户选择的关注目标 */
    public static final String KEY_FOCUS_GOAL = "focus_goal";
    public static final String DEFAULT_FOCUS_GOAL = "建立记录习惯";
}
