package com.example.emoscope;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.example.emoscope.fragments.HistoryFragment;
import com.example.emoscope.fragments.RadarFragment;
import com.example.emoscope.fragments.SettingsFragment;
import com.example.emoscope.fragments.WorkshopFragment;
import com.example.emoscope.viewmodels.HistoryViewModel;
import com.example.emoscope.viewmodels.RadarViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.MaterialColors;
import com.google.mediapipe.tasks.components.containers.Category;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EmoScope 主 Activity — 应用中枢，管理 Fragment 导航、相机引擎、
 * 面容分析、语音识别、AI 调用和 SOS 呼吸急救。
 *
 * 架构: 单 Activity + 3 Fragment (Radar / History / Settings)
 *       数据通过 ViewModel 层在 Fragment 与 Activity 之间流转。
 */
public class MainActivity extends AppCompatActivity
        implements SensorEventListener, RadarFragment.Callback {

    // ── 全局引擎 ──────────────────────────────────────────────────
    private ExecutorService backgroundExecutor;
    EmoDatabaseHelper dbHelper;
    private DeepSeekClient deepSeekClient;
    private FaceAnalyzer faceAnalyzer;
    private CameraEmotionController cameraController;
    private BreathingEngine breathingEngine;
    private SosInterventionController sosController;
    private VoiceRecognitionController voiceController;
    private TextToSpeech tts;
    private SensorManager sensorManager;
    private Sensor accelerometer;

    // ── ViewModels ────────────────────────────────────────────────
    private RadarViewModel radarVM;
    private HistoryViewModel historyVM;

    // ── Fragments ─────────────────────────────────────────────────
    private RadarFragment radarFragment;
    private WorkshopFragment workshopFragment;
    private HistoryFragment historyFragment;
    private SettingsFragment settingsFragment;
    private Fragment activeFragment;

    // ── Activity 级别视图 ─────────────────────────────────────────
    private BottomNavigationView bottomNav;
    private View layoutBreathing, layoutCameraMode;
    private PreviewView viewFinder;
    private ImageView tvCameraFaceEmoji;
    private TextView tvCameraFaceState, tvCameraProb1, tvCameraProb2, tvCameraProb3;
    private TextView tvBreathText;
    private View breathCircle, btnCloseBreath, btnCallHotline;
    private BreathingOverlayView breathOverlay;

    // ── 相机引擎 ──────────────────────────────────────────────────
    // ── 语音引擎 ──────────────────────────────────────────────────
    private volatile boolean isVoiceRecording = false;
    private long voiceRecordStartTime = 0;

    // ── 安全状态 ──────────────────────────────────────────────────
    private long lastShakeTime = 0;

    // ── 数据缓存 ──────────────────────────────────────────────────
    private String currentFaceTop3Desc = "平静专注 100%";
    private String currentLightDesc = "感知中...";
    private volatile FaceAnalyzer.EmotionResult latestEmotionResult = null;

    // ── 拍照打分视图 ──────────────────────────────────────────────
    private View btnCaptureFace, cardFaceResult, btnSaveCapture, btnDiscardCapture;
    private TextView tvCaptureScore, tvCaptureEmotions;

    // ── UI 辅助 ───────────────────────────────────────────────────
    private android.os.Handler countdownHandler;
    private Runnable countdownRunnable;
    private int voiceCountdownSec = 0;

    // ═══════════════════════════════════════════════════════════════
    // 生命周期
    // ═══════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 处理系统栏内边距，避免内容被状态栏遮挡
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragmentContainer), (v, insets) -> {
            int top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        // 初始化执行器与数据库
        backgroundExecutor = Executors.newSingleThreadExecutor();
        dbHelper = new EmoDatabaseHelper(this);

        // 初始化 ViewModels
        radarVM = new ViewModelProvider(this).get(RadarViewModel.class);
        historyVM = new ViewModelProvider(this).get(HistoryViewModel.class);

        // 初始化 DeepSeek 客户端
        deepSeekClient = new DeepSeekClient(this, backgroundExecutor, dbHelper,
                new DeepSeekClient.AiCallback() {
                    @Override public void onAiStarted() {
                        runOnUiThread(() -> radarVM.setAiStarted());
                    }
                    @Override public void onAiResponse(String reply, String fp, String st,
                                                       String ld, boolean isPos) {
                        runOnUiThread(() -> {
                            if (radarFragment != null) radarFragment.showTypewriterEffect(reply);
                            if (isTtsEnabled() && tts != null && !isFinishing()) {
                                tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null);
                            }
                        });
                        saveToDatabase("自动分析",
                                "环境:" + ld + " | 面部:" + fp +
                                "\n原话:" + st + "\n回复: " + reply, isPos);
                    }
                    @Override public void onAiError(String msg) {
                        runOnUiThread(() -> {
                            radarVM.setAiError(msg);
                            showUserMessage(msg);
                        });
                    }
                });

        // 加载偏好设置
        loadPreferences();

        // TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && !isFinishing()) {
                tts.setLanguage(Locale.CHINESE);
                tts.setPitch(1.1f);
            }
        });

        // 传感器
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // 面容分析器
        faceAnalyzer = new FaceAnalyzer(new FaceAnalyzer.FaceCallback() {
            @Override public void onEmotionResult(FaceAnalyzer.EmotionResult r) {
                latestEmotionResult = r; // 缓存最新结果供拍照打分使用
                currentFaceTop3Desc = r.cameraState;
                radarVM.setFaceResult(R.drawable.ic_face_scan,
                        r.prob1, r.prob2, r.prob3,
                        R.drawable.ic_emotion_calm, r.moodLabel);
                tvCameraFaceEmoji.setImageResource(R.drawable.ic_face_scan);
                tvCameraFaceState.setText(r.cameraState + "  |  " + r.moodLabel);
                if (tvCameraProb1 != null) {
                    tvCameraProb1.setText("① " + r.prob1);
                    tvCameraProb1.setVisibility(View.VISIBLE);
                }
                if (tvCameraProb2 != null) {
                    tvCameraProb2.setText("② " + r.prob2);
                    tvCameraProb2.setVisibility(View.VISIBLE);
                }
                if (tvCameraProb3 != null) {
                    tvCameraProb3.setText("③ " + r.prob3);
                    tvCameraProb3.setVisibility(View.VISIBLE);
                }
                triggerSOSButton(r.isWarning);
            }
            @Override public void onNoFace() {
                radarVM.setNoFace();
                tvCameraFaceEmoji.setImageResource(R.drawable.ic_face_scan);
                tvCameraFaceState.setText(R.string.face_no_face);
                if (tvCameraProb1 != null) tvCameraProb1.setVisibility(View.GONE);
                if (tvCameraProb2 != null) tvCameraProb2.setVisibility(View.GONE);
                if (tvCameraProb3 != null) tvCameraProb3.setVisibility(View.GONE);
                triggerSOSButton(false);
            }
        });

        // 绑定 Activity 级别视图
        bindActivityViews();

        cameraController = new CameraEmotionController(this, this, viewFinder,
                backgroundExecutor, new CameraEmotionController.Callback() {
                    @Override public void onLightState(int iconRes, String description) {
                        runOnUiThread(() -> {
                            currentLightDesc = description;
                            radarVM.setLightState(iconRes, description);
                        });
                    }

                    @Override public void onFaceBlendshapes(
                            List<List<Category>> blendshapes, long timestampMs) {
                        runOnUiThread(() -> faceAnalyzer.analyze(blendshapes, timestampMs));
                    }

                    @Override public void onNoFace() {
                        runOnUiThread(() -> faceAnalyzer.analyze(null, 0));
                    }

                    @Override public void onCameraError(String message) {
                        runOnUiThread(() -> {
                            radarVM.setNoFace();
                            showUserMessage(message);
                        });
                    }
                });

        // 呼吸引擎
        breathingEngine = new BreathingEngine(breathCircle, new BreathingEngine.BreathCallback() {
            @Override public void onPhaseChange(String text) { tvBreathText.setText(text); }
            @Override public void onVibrate(int fbConstant) { triggerHaptic(breathCircle, fbConstant); }
            @Override public void onCycleEnd() { /* loop */ }
        });

        sosController = new SosInterventionController(this, breathingEngine, layoutBreathing,
                breathOverlay, new SosInterventionController.Host() {
                    @Override public void triggerHaptic(View view, int feedbackConstant) {
                        MainActivity.this.triggerHaptic(view, feedbackConstant);
                    }

                    @Override public boolean checkSmsPermission() {
                        return MainActivity.this.checkSmsPermission();
                    }

                    @Override public void requestSmsPermission() {
                        MainActivity.this.requestSmsPermission();
                    }

                    @Override public String emergencyContact() {
                        return secureStorage().get(Constants.KEY_CONTACT, "");
                    }

                    @Override public void setSosVisible(boolean visible) {
                        triggerSOSButton(visible);
                    }

                    @Override public void showMessage(String message) {
                        runOnUiThread(() -> showUserMessage(message));
                    }
                });

        voiceController = new VoiceRecognitionController(this, new VoiceRecognitionController.Callback() {
            @Override public void onReady() {
                runOnUiThread(() -> radarVM.setVoiceListening());
            }

            @Override public void onProcessing() {
                runOnUiThread(() -> radarVM.setVoiceButtonText("处理中..."));
            }

            @Override public void onPartialText(String text) {
                runOnUiThread(() -> radarVM.setVoiceText("\"" + text + "\""));
            }

            @Override public void onFinalText(String text) {
                runOnUiThread(() -> handleVoiceResult(text));
            }

            @Override public void onNoSpeech() {
                runOnUiThread(() -> {
                    stopRecording();
                    radarVM.setVoiceNotHeard();
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    stopRecording();
                    radarVM.setVoiceText(message);
                    showUserMessage(message);
                });
            }
        });

        // 初始化 Fragment
        initFragments();

        // 设置导航
        setupBottomNav();

        // 设置叠加层交互
        setupOverlayInteractions();

        setupBreathingCircleBackground();

        // 首次启动引导
        showFirstLaunchGuide();

        // 通知渠道
        NotificationHelper.createChannels(this);

        // 启动相机
        if (checkCorePermissions()) { setupVisualEngine(); startCamera(); }
        else { requestCorePermissions(); }
    }

    private int getTabIndex(Fragment f) {
        if (f == radarFragment) return 0;
        if (f == workshopFragment) return 1;
        if (f == historyFragment) return 2;
        return 3;
    }

    // ═══════════════════════════════════════════════════════════════
    // Fragment 管理
    // ═══════════════════════════════════════════════════════════════
    private void initFragments() {
        radarFragment = new RadarFragment();
        workshopFragment = new WorkshopFragment();
        historyFragment = new HistoryFragment();
        settingsFragment = new SettingsFragment();

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(R.id.fragmentContainer, settingsFragment, "settings").hide(settingsFragment);
        ft.add(R.id.fragmentContainer, historyFragment, "history").hide(historyFragment);
        ft.add(R.id.fragmentContainer, workshopFragment, "workshop").hide(workshopFragment);
        ft.add(R.id.fragmentContainer, radarFragment, "radar");
        ft.commitNow();

        activeFragment = radarFragment;
    }

    private void setupBottomNav() {
        bottomNav.setSelectedItemId(R.id.navRadar);
        bottomNav.setOnItemSelectedListener(item -> {
            triggerHaptic(bottomNav, HapticFeedbackConstants.VIRTUAL_KEY);
            int id = item.getItemId();
            Fragment target;
            if (id == R.id.navRadar) target = radarFragment;
            else if (id == R.id.navWorkshop) { target = workshopFragment; workshopFragment.refreshUI(); }
            else if (id == R.id.navHistory) { target = historyFragment; historyFragment.loadHistoryData(); }
            else { target = settingsFragment; settingsFragment.refreshUI(); }

            if (target == activeFragment) return true;

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.hide(activeFragment);
            ft.show(target);
            ft.commitNow();

            activeFragment = target;
            return true;
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // RadarFragment.Callback 实现
    // ═══════════════════════════════════════════════════════════════
    @Override public void onFaceCardClicked() {
        triggerHaptic(getWindow().getDecorView(), HapticFeedbackConstants.VIRTUAL_KEY);
        if (!checkCorePermissions()) {
            showCorePermissionDialog();
            return;
        }
        latestEmotionResult = null;
        cardFaceResult.setVisibility(View.GONE);
        fragmentContainer().setVisibility(View.GONE);
        bottomNav.setVisibility(View.GONE);
        layoutCameraMode.setAlpha(0f);
        layoutCameraMode.setVisibility(View.VISIBLE);
        layoutCameraMode.animate().alpha(1f).setDuration(300).start();
    }

    @Override public void onSOSClicked() {
        lastShakeTime = System.currentTimeMillis();
        showSOSCountdown();
    }

    @Override public void onTtsToggled() {
        boolean ttsOn = !isTtsEnabled();
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(Constants.KEY_TTS, ttsOn).apply();
        updateTtsState(ttsOn);
        if (!ttsOn && tts != null && tts.isSpeaking()) tts.stop();
    }

    @Override public void onVoiceButtonPressed() {
        // 先检查录音权限 — 独立请求，不捆绑相机
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.RECORD_AUDIO)) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("需要麦克风权限")
                        .setMessage("语音情绪识别需要访问麦克风，用于将你说的话转成文字。录音数据仅在本地处理。")
                        .setPositiveButton("允许", (d, w) -> requestAudioPermission())
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                requestAudioPermission();
            }
            return;
        }
        // 检查系统语音服务是否可用
        if (voiceController != null && !voiceController.isAvailable()) {
            showUserMessage("当前设备没有可用的语音识别服务，请安装 Google 语音搜索或 Google App");
            return;
        }
        boolean clickMode = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.KEY_VOICE_CLICK_MODE, Constants.DEFAULT_VOICE_CLICK_MODE);

        if (clickMode) {
            // 点击切换模式：点一下开始 → 再点一下停止
            if (isVoiceRecording) {
                stopRecording();
            } else {
                isVoiceRecording = true;
                triggerHaptic(findViewById(R.id.btnContainerMain), HapticFeedbackConstants.VIRTUAL_KEY);
                voiceRecordStartTime = System.currentTimeMillis();
                startVoiceRecognition();
                animateVoiceButton(true);
                if (tts != null && tts.isSpeaking()) tts.stop();
                radarVM.setRecording(true);
                radarVM.setVoiceListening();
                startWaveAnimation();
                // 启动 30 秒自动停止倒计时
                startVoiceCountdown();
            }
            return;
        }

        // 长按模式：按下开始录音
        if (isVoiceRecording) {
            stopRecording();
            return;
        }
        isVoiceRecording = true;
        triggerHaptic(findViewById(R.id.btnContainerMain), HapticFeedbackConstants.VIRTUAL_KEY);
        voiceRecordStartTime = System.currentTimeMillis();
        startVoiceRecognition();

        // 按钮动画
        animateVoiceButton(true);
        if (tts != null && tts.isSpeaking()) tts.stop();
        radarVM.setRecording(true);
        radarVM.setVoiceListening();
        startWaveAnimation();
    }

    private void animateVoiceButton(boolean pressed) {
        View container = findViewById(R.id.btnContainerMain);
        if (container == null) return;
        if (pressed) {
            if (!isVoiceRecording) return;
            // 缩小 + 启动脉冲循环
            container.animate().scaleX(0.92f).scaleY(0.92f).setDuration(120)
                    .withEndAction(() -> {
                        if (!isVoiceRecording) return;
                        container.animate().scaleX(1.03f).scaleY(1.03f).setDuration(600)
                                .withEndAction(() -> {
                                    if (!isVoiceRecording) return;
                                    container.animate().scaleX(0.97f).scaleY(0.97f)
                                            .setDuration(600)
                                            .withEndAction(() -> animateVoiceButton(true))
                                            .start();
                                }).start();
                    }).start();
        } else {
            container.animate().cancel();
            container.animate().scaleX(1f).scaleY(1f).setDuration(250)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void startWaveAnimation() {
        runOnUiThread(() -> {
            View wave = findViewById(R.id.voiceWaveView);
            View sonar = findViewById(R.id.sonarRipple);
            if (wave instanceof VoiceWaveView) ((VoiceWaveView) wave).start();
            if (sonar instanceof SonarRippleView) ((SonarRippleView) sonar).start();
        });
    }

    private void stopWaveAnimation() {
        runOnUiThread(() -> {
            View wave = findViewById(R.id.voiceWaveView);
            View sonar = findViewById(R.id.sonarRipple);
            if (wave instanceof VoiceWaveView) ((VoiceWaveView) wave).stop();
            if (sonar instanceof SonarRippleView) ((SonarRippleView) sonar).stop();
        });
    }

    private void startVoiceCountdown() {
        voiceCountdownSec = Constants.VOICE_AUTO_STOP_SEC;
        if (countdownHandler == null) countdownHandler = new android.os.Handler(getMainLooper());
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                voiceCountdownSec--;
                if (voiceCountdownSec > 0 && isVoiceRecording) {
                    radarVM.setVoiceButtonText(String.format(
                            getString(R.string.voice_countdown), voiceCountdownSec));
                    countdownHandler.postDelayed(this, 1000);
                } else if (voiceCountdownSec <= 0 && isVoiceRecording) {
                    stopRecording();
                }
            }
        };
        countdownHandler.postDelayed(countdownRunnable, 1000);
    }

    private void cancelVoiceCountdown() {
        if (countdownHandler != null && countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
    }

    @Override public void onVoiceButtonReleased() {
        boolean clickMode = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.KEY_VOICE_CLICK_MODE, Constants.DEFAULT_VOICE_CLICK_MODE);
        // 点击模式：松手不做任何事（通过第二次点击停止）
        if (clickMode) return;
        // 长按模式：松手即停止
        stopRecording();
    }

    @Override public void onQuickMoodClicked() {
        triggerHaptic(getWindow().getDecorView(), HapticFeedbackConstants.VIRTUAL_KEY);
        showQuickMoodDialog();
    }

    @Override public void onDailyCareSecondaryClicked() {
        triggerHaptic(getWindow().getDecorView(), HapticFeedbackConstants.VIRTUAL_KEY);
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.navWorkshop);
    }

    /** 快速心情记录弹窗 — 复用 HistoryFragment 的心情打卡逻辑 */
    public void showQuickMoodDialog() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(32, 24, 32, 16);

        final int[] selectedIdx = {-1};

        for (int row = 0; row < 2; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(android.view.Gravity.CENTER);
            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(android.view.Gravity.CENTER);
                item.setPadding(16, 8, 16, 8);
                item.setClickable(true);
                item.setBackgroundColor(0x00000000);

                ImageView icon = new ImageView(this);
                icon.setImageResource(Constants.MANUAL_MOOD_ICONS[idx]);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        (int) (40 * getResources().getDisplayMetrics().density),
                        (int) (40 * getResources().getDisplayMetrics().density));
                icon.setLayoutParams(iconParams);

                TextView label = new TextView(this);
                label.setText(Constants.MANUAL_MOOD_LABELS[idx]);
                label.setTextSize(12);
                label.setTextColor(MaterialColors.getColor(this,
                        com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                label.setGravity(android.view.Gravity.CENTER);
                label.setPadding(0, 4, 0, 0);

                final int finalIdx = idx;
                item.setOnClickListener(v -> {
                    selectedIdx[0] = finalIdx;
                    for (int i = 0; i < grid.getChildCount(); i++) {
                        LinearLayout r = (LinearLayout) grid.getChildAt(i);
                        for (int j = 0; j < r.getChildCount(); j++) {
                            r.getChildAt(j).setBackgroundColor(0x00000000);
                        }
                    }
                    item.setBackgroundColor(0x20B794F4);
                });
                item.addView(icon);
                item.addView(label);
                rowLayout.addView(item, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
            grid.addView(rowLayout);
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("快速记录心情")
                .setView(grid)
                .setPositiveButton("记录", (dialog, which) -> {
                    if (selectedIdx[0] < 0) return;
                    String label = Constants.MANUAL_MOOD_LABELS[selectedIdx[0]];
                    boolean isPos = selectedIdx[0] <= 2;
                    saveToDatabase("手动记录", "心情: " + label, isPos);
                    // 更新打卡天数
                    updateStreakFromMain();
                    refreshDailyLoopSoon();
                    showUserMessage("已记录：" + label);
                })
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show();
    }

    private void updateStreakFromMain() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault());
        String today = sdf.format(new java.util.Date());
        android.content.SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String lastDate = prefs.getString(Constants.KEY_LAST_RECORD_DATE, "");
        int streak = prefs.getInt(Constants.KEY_STREAK_COUNT, 0);

        if (today.equals(lastDate)) return;
        if (!lastDate.isEmpty()) {
            try {
                java.util.Date last = sdf.parse(lastDate);
                java.util.Date now = sdf.parse(today);
                long diff = (now.getTime() - last.getTime()) / (1000 * 60 * 60 * 24);
                if (diff == 1) streak++;
                else streak = 1;
            } catch (Exception e) { streak = 1; }
        } else {
            streak = 1;
        }
        prefs.edit().putString(Constants.KEY_LAST_RECORD_DATE, today)
                .putInt(Constants.KEY_STREAK_COUNT, streak).apply();
    }

    private void refreshDailyLoopSoon() {
        findViewById(android.R.id.content).postDelayed(() -> {
            if (radarFragment != null) radarFragment.refreshDailyLoop();
            if (historyFragment != null) historyFragment.loadHistoryData();
            if (workshopFragment != null) workshopFragment.refreshUI();
        }, 250);
    }

    private void stopRecording() {
        if (!isVoiceRecording) return;
        isVoiceRecording = false;
        cancelVoiceCountdown();
        stopWaveAnimation();
        triggerHaptic(findViewById(R.id.btnContainerMain), HapticFeedbackConstants.VIRTUAL_KEY);
        stopVoiceRecognition();

        animateVoiceButton(false);
        radarVM.setRecording(false);
    }

    // ═══════════════════════════════════════════════════════════════
    // 覆盖层交互 (Camera / Breathing)
    // ═══════════════════════════════════════════════════════════════
    private void setupOverlayInteractions() {
        findViewById(R.id.btnCloseCamera).setOnClickListener(v -> {
            triggerHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
            layoutCameraMode.animate().cancel();
            layoutCameraMode.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                layoutCameraMode.setVisibility(View.GONE);
                layoutCameraMode.setAlpha(1f);
                fragmentContainer().setVisibility(View.VISIBLE);
                bottomNav.setVisibility(View.VISIBLE);
            }).start();
        });

        findViewById(R.id.btnFlipCamera).setOnClickListener(v -> {
            triggerHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
            v.animate().rotationBy(180).setDuration(300).start();
            if (cameraController != null) cameraController.flipCamera();
        });

        // ══ 拍照打分按钮 ══
        btnCaptureFace.setOnClickListener(v -> {
            triggerHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
            captureFaceScore();
        });

        btnSaveCapture.setOnClickListener(v -> saveCaptureResult());
        btnDiscardCapture.setOnClickListener(v -> discardCaptureResult());

        btnCloseBreath.setOnClickListener(v -> {
            triggerHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
            stopBreathingIntervention();
        });

        btnCallHotline.setOnClickListener(v -> {
            triggerHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
            startActivity(new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:" + Constants.HOTLINE_NUMBER)));
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 拍照打分 — 核心功能
    // ═══════════════════════════════════════════════════════════════
    private void captureFaceScore() {
        FaceAnalyzer.EmotionResult r = latestEmotionResult;
        if (r == null) {
            showUserMessage("尚未检测到面部，请正对摄像头");
            return;
        }

        // 显示结果卡片
        tvCaptureScore.setText(String.valueOf(r.weightedScore));
        tvCaptureEmotions.setText("① " + r.prob1 + "\n② " + r.prob2 + "\n③ " + r.prob3);
        cardFaceResult.setVisibility(View.VISIBLE);
        cardFaceResult.setAlpha(0f);
        cardFaceResult.setScaleX(0.85f);
        cardFaceResult.setScaleY(0.85f);
        cardFaceResult.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // 拍照按钮视觉反馈
        btnCaptureFace.animate()
                .scaleX(1.15f).scaleY(1.15f)
                .setDuration(120)
                .withEndAction(() ->
                    btnCaptureFace.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
                .start();
    }

    private void saveCaptureResult() {
        FaceAnalyzer.EmotionResult r = latestEmotionResult;
        if (r == null) return;

        String detail = "面容快照 | 加权分: " + r.weightedScore
                + " | ①" + r.prob1 + " ②" + r.prob2 + " ③" + r.prob3;
        boolean positive = r.weightedScore >= 50;

        backgroundExecutor.execute(() -> {
            dbHelper.saveRecord("面容分析", detail, positive);
            runOnUiThread(() -> {
                showUserMessage("已保存 · 情绪分 " + r.weightedScore + "/100");
                resetCaptureUI();
                // 刷新首页数据
                if (radarFragment != null) radarFragment.refreshDailyLoop();
            });
        });
    }

    private void discardCaptureResult() {
        showUserMessage("已丢弃");
        resetCaptureUI();
    }

    private void resetCaptureUI() {
        cardFaceResult.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            cardFaceResult.setVisibility(View.GONE);
            cardFaceResult.setAlpha(1f);
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════
    // SOS / 呼吸 / 短信
    // ═══════════════════════════════════════════════════════════════
    private void triggerSOSButton(boolean show) {
        runOnUiThread(() -> radarVM.setSosVisible(show));
    }

    private void showSOSCountdown() {
        if (sosController != null) sosController.showCountdown();
    }

    private void stopBreathingIntervention() {
        lastShakeTime = System.currentTimeMillis();
        if (sosController != null) sosController.stopBreathingIntervention();
    }

    // ═══════════════════════════════════════════════════════════════
    // 设置变更回调 (供 SettingsFragment 调用)
    // ═══════════════════════════════════════════════════════════════
    public void onTtsSettingChanged(boolean enabled) { updateTtsState(enabled); }
    public void onApiKeyChanged(String newKey) { deepSeekClient.setApiKey(newKey); }
    public void onPrivacyModeChanged() { applyPrivacyMode(); }

    /** B1: 为手动记录提供 AI 情绪解读 */
    public void requestManualMoodAnalysis(String moodDetail) {
        // 简化调用：将心情详情作为"话语"传给 AI，面部和环境信息用占位符
        deepSeekClient.call("手动记录 情绪分析", moodDetail, "手动记录", "室内环境");
    }

    private void updateTtsState(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(Constants.KEY_TTS, enabled).apply();
        radarVM.setTtsIcon(enabled ? R.drawable.ic_tts_on : R.drawable.ic_tts_off);
    }

    // ═══════════════════════════════════════════════════════════════
    // 公开访问器 (供 Fragments 获取共享资源)
    // ═══════════════════════════════════════════════════════════════
    public EmoDatabaseHelper getDbHelper() { return dbHelper; }
    public ExecutorService getBackgroundExecutor() { return backgroundExecutor; }
    private boolean isTtsEnabled() {
        return getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.KEY_TTS, Constants.DEFAULT_TTS);
    }
    private SecureStorage secureStorage() { return new SecureStorage(this); }
    private View fragmentContainer() { return findViewById(R.id.fragmentContainer); }

    // ═══════════════════════════════════════════════════════════════
    // 传感器
    // ═══════════════════════════════════════════════════════════════
    @Override protected void onResume() {
        super.onResume();
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);
        applyPrivacyMode();
    }
    @Override protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }
    @Override protected void onStop() {
        super.onStop();
        // 隐私模式：切后台时自动返回 Radar 页
        if (getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.KEY_PRIVACY_MODE, false)) {
            if (activeFragment != radarFragment && bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.navRadar);
            }
        }
    }

    private void applyPrivacyMode() {
        boolean enabled = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.KEY_PRIVACY_MODE, false);
        if (enabled) {
            getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (layoutBreathing.getVisibility() == View.VISIBLE) return;

        float x = event.values[0], y = event.values[1], z = event.values[2];
        float gForce = (float) Math.sqrt(x*x + y*y + z*z) / SensorManager.GRAVITY_EARTH;

        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        float threshold = prefs.getFloat(Constants.KEY_SHAKE_THRESH, Constants.DEFAULT_SHAKE_THRESHOLD);

        if (gForce > threshold) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > Constants.SHAKE_COOLDOWN_MS) {
                lastShakeTime = now;
                showSOSCountdown();
            }
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ═══════════════════════════════════════════════════════════════
    // 相机 + 面容分析
    // ═══════════════════════════════════════════════════════════════
    private void setupVisualEngine() {
        if (cameraController != null) cameraController.setupVisualEngine();
    }

    private void startCamera() {
        if (cameraController != null) cameraController.startCamera();
    }

    private void startVoiceRecognition() {
        if (voiceController != null) voiceController.start();
    }

    private void stopVoiceRecognition() {
        if (voiceController != null) voiceController.stop();
    }

    private void handleVoiceResult(String text) {
        if (text.isEmpty()) {
            radarVM.setVoiceNotHeard();
            return;
        }
        float durationSec = Math.max(0.5f,
                (System.currentTimeMillis() - voiceRecordStartTime) / 1000f);
        float speed = text.length() / durationSec;
        String speedDesc = "声带平稳";
        if (speed > 4.5f) speedDesc = "急促/高压";
        else if (speed < 1.5f) speedDesc = "迟缓/低落";

        radarVM.setVoiceResult("\"" + text + "\"",
                String.format(Locale.getDefault(), "%.1f字/秒 (%s)", speed, speedDesc));
        deepSeekClient.call(currentFaceTop3Desc, text, speedDesc, currentLightDesc);
    }


    // ═══════════════════════════════════════════════════════════════
    // 权限
    // ═══════════════════════════════════════════════════════════════
    private boolean checkCorePermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCorePermissions() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
                || ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.RECORD_AUDIO)) {
            showCorePermissionDialog();
            return;
        }
        requestCorePermissionsDirect();
    }

    private void requestCorePermissionsDirect() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                Constants.PERM_CORE);
    }

    private void requestAudioPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                Constants.PERM_AUDIO);
    }

    private void requestSmsPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.perm_sms_title)
                    .setMessage(R.string.perm_sms_rationale)
                    .setPositiveButton(R.string.perm_authorize, (d, w) ->
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.SEND_SMS}, Constants.PERM_SMS))
                    .setNegativeButton(getString(R.string.dialog_cancel), null).show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS}, Constants.PERM_SMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == Constants.PERM_CORE) {
            if (results.length >= 2 && results[0] == PackageManager.PERMISSION_GRANTED
                    && results[1] == PackageManager.PERMISSION_GRANTED) {
                setupVisualEngine(); startCamera();
            } else {
                showCorePermissionDeniedDialog();
            }
        } else if (requestCode == Constants.PERM_AUDIO) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                showUserMessage("麦克风权限已开启，现在可以对语音按钮说话了");
            } else {
                showUserMessage("需要麦克风权限才能使用语音功能，请在系统设置中开启");
            }
        } else if (requestCode == Constants.PERM_SMS && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            showUserMessage("SOS 短信权限已就绪，请再次触发 SOS 发送求助短信");
        } else if (requestCode == Constants.PERM_SMS) {
            showUserMessage("未获得短信权限，SOS 将只启动呼吸干预，不会发送短信");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════════
    private void bindActivityViews() {
        bottomNav = findViewById(R.id.bottomNav);
        layoutBreathing = findViewById(R.id.layoutBreathing);
        layoutCameraMode = findViewById(R.id.layoutCameraMode);
        viewFinder = findViewById(R.id.viewFinder);
        tvCameraFaceEmoji = findViewById(R.id.tvCameraFaceEmoji);
        tvCameraFaceState = findViewById(R.id.tvCameraFaceState);
        tvCameraProb1 = findViewById(R.id.tvCameraProb1);
        tvCameraProb2 = findViewById(R.id.tvCameraProb2);
        tvCameraProb3 = findViewById(R.id.tvCameraProb3);
        tvBreathText = findViewById(R.id.tvBreathText);
        breathCircle = findViewById(R.id.breathCircle);
        breathOverlay = findViewById(R.id.breathOverlay);
        btnCloseBreath = findViewById(R.id.btnCloseBreath);
        btnCallHotline = findViewById(R.id.btnCallHotline);
        // 拍照打分
        btnCaptureFace = findViewById(R.id.btnCaptureFace);
        cardFaceResult = findViewById(R.id.cardFaceResult);
        btnSaveCapture = findViewById(R.id.btnSaveCapture);
        btnDiscardCapture = findViewById(R.id.btnDiscardCapture);
        tvCaptureScore = findViewById(R.id.tvCaptureScore);
        tvCaptureEmotions = findViewById(R.id.tvCaptureEmotions);
    }

    private void showCorePermissionDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("需要相机和麦克风权限")
                .setMessage("相机用于本地面部情绪分析，麦克风用于系统语音识别。不开启权限时，你仍可使用手动记录和历史查看。")
                .setPositiveButton("继续授权", (d, w) -> requestCorePermissionsDirect())
                .setNegativeButton("暂不授权", null)
                .show();
    }

    private void showCorePermissionDeniedDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("部分核心功能不可用")
                .setMessage("未获得相机或麦克风权限，面部分析和语音记录将暂停。你可以在系统设置中重新开启权限。")
                .setPositiveButton("去系统设置", (d, w) -> openAppSettings())
                .setNegativeButton("稍后再说", null)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void showUserMessage(String message) {
        if (message == null || message.trim().isEmpty() || isFinishing()) return;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setupBreathingCircleBackground() {
        GradientDrawable bthBg = new GradientDrawable();
        bthBg.setShape(GradientDrawable.OVAL);
        bthBg.setColor(ContextCompat.getColor(this, R.color.overlay_white));
        breathCircle.setBackground(bthBg);
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String apiKey = secureStorage().get(Constants.KEY_API_KEY, Constants.DEFAULT_API_KEY);
        deepSeekClient.setApiKey(apiKey);
        radarVM.setTtsIcon(prefs.getBoolean(Constants.KEY_TTS, Constants.DEFAULT_TTS)
                ? R.drawable.ic_tts_on : R.drawable.ic_tts_off);
    }

    private void triggerHaptic(View view, int feedbackConstant) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(Constants.KEY_HAPTIC, Constants.DEFAULT_HAPTIC) && view != null) {
            view.performHapticFeedback(feedbackConstant);
        }
    }

    private void saveToDatabase(String type, String detail, boolean positive) {
        backgroundExecutor.execute(() -> dbHelper.saveRecord(type, detail, positive));
    }

    private int getEmotionIcon(String emoji) {
        switch (emoji) {
            case "😆": return R.drawable.ic_emotion_joy;
            case "😭": return R.drawable.ic_emotion_sad;
            case "😟": return R.drawable.ic_emotion_tense;
            case "😱": return R.drawable.ic_emotion_surprise;
            case "🥱": return R.drawable.ic_emotion_fatigue;
            case "😐": return R.drawable.ic_emotion_calm;
            default:   return R.drawable.ic_face_scan;
        }
    }

    private void showFirstLaunchGuide() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(Constants.KEY_FIRST_LAUNCH, false)) return;
        prefs.edit().putBoolean(Constants.KEY_FIRST_LAUNCH, true).apply();

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("欢迎使用 EmoScope")
                .setMessage("欢迎来到 EmoScope，你的 AI 情绪成长伙伴。\n\n"
                        + "[记录] 对麦克风说话或手动记录，捕捉每一天的情绪瞬间\n\n"
                        + "[理解] AI 自动分析你的情绪模式、压力来源和开心时刻\n\n"
                        + "[成长] 持续记录，解锁成就徽章和成长等级\n\n"
                        + "相机仅用于本地面部分析，麦克风用于系统语音识别。AI 解读会把必要文本发送到你配置的 AI 服务。\n\n"
                        + "EmoScope 不提供医疗诊断，也不能替代心理咨询或紧急救援。摇晃手机或在“我的”页配置 SOS 紧急求助。")
                .setPositiveButton("选择目标", (dialog, which) -> showFocusGoalDialog())
                .setCancelable(false)
                .show();
    }

    private void showFocusGoalDialog() {
        String[] goals = {"建立记录习惯", "减压", "睡眠前整理", "识别低落周期"};
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String current = prefs.getString(Constants.KEY_FOCUS_GOAL, Constants.DEFAULT_FOCUS_GOAL);
        int checked = 0;
        for (int i = 0; i < goals.length; i++) {
            if (goals[i].equals(current)) {
                checked = i;
                break;
            }
        }

        final int[] selected = {checked};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("这段时间你更想关注什么？")
                .setSingleChoiceItems(goals, checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton("开始", (dialog, which) -> {
                    prefs.edit().putString(Constants.KEY_FOCUS_GOAL, goals[selected[0]]).apply();
                    if (radarFragment != null) radarFragment.refreshDailyLoop();
                    showUserMessage("已设置目标：" + goals[selected[0]]);
                })
                .setCancelable(false)
                .show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (breathingEngine != null) breathingEngine.stop();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (backgroundExecutor != null) backgroundExecutor.shutdown();
        if (cameraController != null) cameraController.release();
        if (voiceController != null) voiceController.stop();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }
}
