package com.example.emoscope;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
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
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.nio.ByteBuffer;
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
    private BreathingEngine breathingEngine;
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
    private ProcessCameraProvider cameraProvider;
    private FaceLandmarker faceLandmarker;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;

    // ── 语音引擎 ──────────────────────────────────────────────────
    private android.speech.SpeechRecognizer androidRecognizer;
    private volatile boolean isVoiceRecording = false;
    private long voiceRecordStartTime = 0;
    private StringBuilder partialText = new StringBuilder();

    // ── 安全状态 ──────────────────────────────────────────────────
    private boolean hasSentSmsThisSession = false;
    private long lastSmsTime = 0;
    private long lastShakeTime = 0;
    private int breathMode = Constants.BREATH_MODE_BOX;

    // ── 数据缓存 ──────────────────────────────────────────────────
    private String currentFaceTop3Desc = "平静专注 100%";
    private String currentLightDesc = "感知中...";

    // ── UI 辅助 ───────────────────────────────────────────────────
    private GradientDrawable btnMainBg;
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
                    @Override public void onAiStarted() { radarVM.setAiStarted(); }
                    @Override public void onAiResponse(String reply, String fp, String st,
                                                       String ld, boolean isPos) {
                        if (radarFragment != null) radarFragment.showTypewriterEffect(reply);
                        if (isTtsEnabled() && tts != null && !isFinishing()) {
                            tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null);
                        }
                        saveToDatabase("自动分析",
                                "环境:" + ld + " | 面部:" + fp +
                                "\n原话:" + st + "\n回复: " + reply, isPos);
                    }
                    @Override public void onAiError(String msg) { radarVM.setAiError(msg); }
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

        // 呼吸引擎
        breathingEngine = new BreathingEngine(breathCircle, new BreathingEngine.BreathCallback() {
            @Override public void onPhaseChange(String text) { tvBreathText.setText(text); }
            @Override public void onVibrate(int fbConstant) { triggerHaptic(breathCircle, fbConstant); }
            @Override public void onCycleEnd() { /* loop */ }
        });

        // 初始化 Fragment
        initFragments();

        // 设置导航
        setupBottomNav();

        // 设置叠加层交互
        setupOverlayInteractions();

        // 语音按钮梯度
        setupVoiceButtonGradient();

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
                if (btnMainBg != null) {
                    btnMainBg.setColors(new int[]{
                            ContextCompat.getColor(this, R.color.grad_btn_recording_start),
                            ContextCompat.getColor(this, R.color.grad_btn_recording_end)});
                }
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
        if (btnMainBg != null) {
            btnMainBg.setColors(new int[]{
                    ContextCompat.getColor(this, R.color.grad_btn_recording_start),
                    ContextCompat.getColor(this, R.color.grad_btn_recording_end)});
        }
        if (tts != null && tts.isSpeaking()) tts.stop();
        radarVM.setRecording(true);
        radarVM.setVoiceListening();
        startWaveAnimation();
    }

    private void animateVoiceButton(boolean pressed) {
        View container = findViewById(R.id.btnContainerMain);
        if (container == null) return;
        if (pressed) {
            // 缩小 + 启动脉冲循环
            container.animate().scaleX(0.92f).scaleY(0.92f).setDuration(120)
                    .withEndAction(() -> {
                        container.animate().scaleX(1.03f).scaleY(1.03f).setDuration(600)
                                .withEndAction(() -> {
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
                    item.setBackgroundColor(0x206C5CE7);
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

    private void stopRecording() {
        if (!isVoiceRecording) return;
        isVoiceRecording = false;
        cancelVoiceCountdown();
        stopWaveAnimation();
        triggerHaptic(findViewById(R.id.btnContainerMain), HapticFeedbackConstants.VIRTUAL_KEY);
        stopVoiceRecognition();

        animateVoiceButton(false);
        if (btnMainBg != null) {
            btnMainBg.setColors(new int[]{
                    ContextCompat.getColor(this, R.color.grad_btn_start),
                    ContextCompat.getColor(this, R.color.grad_btn_end)});
        }
        radarVM.setRecording(false);
    }

    // ═══════════════════════════════════════════════════════════════
    // 覆盖层交互 (Camera / Breathing)
    // ═══════════════════════════════════════════════════════════════
    private void setupOverlayInteractions() {
        findViewById(R.id.btnCloseCamera).setOnClickListener(v -> {
            triggerHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
            layoutCameraMode.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                layoutCameraMode.setVisibility(View.GONE);
                fragmentContainer().setVisibility(View.VISIBLE);
                bottomNav.setVisibility(View.VISIBLE);
            }).start();
        });

        findViewById(R.id.btnFlipCamera).setOnClickListener(v -> {
            triggerHaptic(v, HapticFeedbackConstants.VIRTUAL_KEY);
            v.animate().rotationBy(180).setDuration(300).start();
            lensFacing = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                    ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
            startCamera();
        });

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
    // SOS / 呼吸 / 短信
    // ═══════════════════════════════════════════════════════════════
    private void triggerSOSButton(boolean show) {
        runOnUiThread(() -> radarVM.setSosVisible(show));
    }

    private void showSOSCountdown() {
        final int[] count = {3};
        final android.os.Handler handler = new android.os.Handler(getMainLooper());
        final android.widget.TextView msgView = new android.widget.TextView(this);
        msgView.setPadding(48, 32, 48, 16);
        msgView.setTextSize(16);
        msgView.setText(String.format(getString(R.string.sos_countdown_message), 3));

        final androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sos_countdown_title))
                .setView(msgView)
                .setNegativeButton(getString(R.string.sos_cancel_now), (d, w) -> {
                    count[0] = -1; // 标记取消
                })
                .setCancelable(false)
                .create();

        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (count[0] < 0) return;
                if (count[0] == 0) {
                    if (dialog.isShowing()) dialog.dismiss();
                    showBreathModeDialog();
                    return;
                }
                msgView.setText(String.format(getString(R.string.sos_countdown_message), count[0]));
                count[0]--;
                handler.postDelayed(this, 1000);
            }
        };
        dialog.show();
        handler.postDelayed(tick, 0);
    }

    private void showBreathModeDialog() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        breathMode = prefs.getInt(Constants.KEY_BREATH_MODE, Constants.BREATH_MODE_BOX);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.breath_mode_title))
                .setSingleChoiceItems(Constants.BREATH_MODE_NAMES, breathMode, (dialog, which) -> {
                    breathMode = which;
                    prefs.edit().putInt(Constants.KEY_BREATH_MODE, which).apply();
                })
                .setPositiveButton("开始", (dialog, which) -> startBreathingIntervention(breathMode))
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show();
    }

    private void startBreathingIntervention(int mode) {
        if (breathingEngine.isRunning()) return;
        triggerHaptic(getWindow().getDecorView(), HapticFeedbackConstants.LONG_PRESS);
        sendEmergencySMS();
        layoutBreathing.setVisibility(View.VISIBLE);
        layoutBreathing.setAlpha(0f);
        layoutBreathing.animate().alpha(1f).setDuration(500).start();
        if (breathOverlay != null) {
            breathOverlay.setVisibility(View.VISIBLE);
            breathOverlay.startBreathing(Constants.BREATH_PHASES[mode][0]);
        }
        breathingEngine.start(mode);
    }

    private void stopBreathingIntervention() {
        hasSentSmsThisSession = false;
        breathingEngine.stop();
        lastShakeTime = System.currentTimeMillis();
        if (breathOverlay != null) breathOverlay.stopBreathing();
        layoutBreathing.animate().alpha(0f).setDuration(500)
                .withEndAction(() -> layoutBreathing.setVisibility(View.GONE)).start();
        triggerSOSButton(false);
    }

    private void sendEmergencySMS() {
        if (hasSentSmsThisSession) return;
        if (System.currentTimeMillis() - lastSmsTime < Constants.SOS_SMS_COOLDOWN_MS) return;
        String contact = secureStorage().get(Constants.KEY_CONTACT, "");
        if (contact.trim().isEmpty()) return;
        if (!checkSmsPermission()) { requestSmsPermission(); return; }
        try {
            SmsManager.getDefault().sendTextMessage(contact, null,
                    getString(R.string.sos_sms_body), null, null);
            hasSentSmsThisSession = true;
            lastSmsTime = System.currentTimeMillis();
        } catch (Exception e) {
            Log.e(Constants.TAG, "SOS SMS failed", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 设置变更回调 (供 SettingsFragment 调用)
    // ═══════════════════════════════════════════════════════════════
    public void onTtsSettingChanged(boolean enabled) { updateTtsState(enabled); }
    public void onApiKeyChanged(String newKey) { deepSeekClient.setApiKey(newKey); }

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
        try {
            BaseOptions base = BaseOptions.builder()
                    .setModelAssetPath(Constants.FACELANDMARKER_MODEL).build();
            FaceLandmarker.FaceLandmarkerOptions ops = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(base).setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::onFaceAnalyzed).setNumFaces(1)
                    .setOutputFaceBlendshapes(true).build();
            faceLandmarker = FaceLandmarker.createFromOptions(this, ops);
        } catch (Throwable t) {
            Log.e(Constants.TAG, "FaceLandmarker init failed", t);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                if (cameraProvider != null) cameraProvider.unbindAll();
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                analysis.setAnalyzer(backgroundExecutor, proxy -> {
                    try {
                        ByteBuffer buffer = proxy.getPlanes()[0].getBuffer();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        int sampleStep = Constants.LUMINANCE_SAMPLE_STEP;
                        long total = 0;
                        int samples = data.length / sampleStep;
                        for (int i = 0; i < data.length; i += sampleStep) {
                            total += (data[i] & 0xFF);
                        }
                        int avgLuminance = (samples > 0) ? (int) (total / samples) : 128;

                        int lightIcon = R.drawable.ic_light_cloud;
                        String desc = "光照舒适";
                        if (avgLuminance < Constants.LUMINANCE_LOW) {
                            lightIcon = R.drawable.ic_light_moon;
                            desc = "昏暗阴沉";
                        } else if (avgLuminance > Constants.LUMINANCE_HIGH) {
                            lightIcon = R.drawable.ic_light_sun;
                            desc = "极度刺眼";
                        }

                        int finalIcon = lightIcon;
                        String finalDesc = desc;
                        runOnUiThread(() -> {
                            currentLightDesc = finalDesc;
                            radarVM.setLightState(finalIcon, finalDesc);
                        });

                        if (faceLandmarker != null) {
                            Bitmap b = proxy.toBitmap();
                            if (b != null) faceLandmarker.detectAsync(
                                    new BitmapImageBuilder(b).build(),
                                    proxy.getImageInfo().getTimestamp() / 1000000);
                        }
                    } finally { proxy.close(); }
                });

                cameraProvider.bindToLifecycle(this,
                        new CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                        preview, analysis);
            } catch (Exception e) {
                Log.e(Constants.TAG, "Camera bind failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onFaceAnalyzed(FaceLandmarkerResult result, MPImage inputImage) {
        if (!result.faceBlendshapes().isPresent() || result.faceBlendshapes().get().isEmpty()) {
            runOnUiThread(() -> faceAnalyzer.analyze(null, 0));
            return;
        }
        List<List<Category>> shapes = result.faceBlendshapes().get();
        long ts = System.currentTimeMillis();
        runOnUiThread(() -> faceAnalyzer.analyze(shapes, ts));
    }

    // ═══════════════════════════════════════════════════════════════
    // 语音识别 — Android 内置 SpeechRecognizer（稳定、免费、中文好）
    // ═══════════════════════════════════════════════════════════════
    private boolean isAndroidRecognizerAvailable() {
        return android.speech.SpeechRecognizer.isRecognitionAvailable(this);
    }

    private void startVoiceRecognition() {
        partialText.setLength(0);
        if (!isAndroidRecognizerAvailable()) {
            runOnUiThread(() -> radarVM.setVoiceNotHeard());
            return;
        }
        if (androidRecognizer != null) androidRecognizer.destroy();
        androidRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
        androidRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                runOnUiThread(() -> radarVM.setVoiceListening());
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                runOnUiThread(() -> radarVM.setVoiceButtonText("处理中..."));
            }
            @Override public void onError(int error) {
                runOnUiThread(() -> radarVM.setVoiceNotHeard());
            }
            @Override public void onResults(Bundle results) {
                java.util.ArrayList<String> matches = results.getStringArrayList(
                        android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    final String text = matches.get(0).trim();
                    runOnUiThread(() -> handleVoiceResult(text));
                } else {
                    runOnUiThread(() -> radarVM.setVoiceNotHeard());
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {
                java.util.ArrayList<String> matches = partialResults.getStringArrayList(
                        android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    final String text = matches.get(0).trim();
                    runOnUiThread(() -> {
                        partialText.setLength(0);
                        partialText.append(text);
                        radarVM.setVoiceText("\"" + text + "\"");
                    });
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        androidRecognizer.startListening(intent);
    }

    private void stopVoiceRecognition() {
        if (androidRecognizer != null) {
            try { androidRecognizer.stopListening(); } catch (Exception e) {}
            try { androidRecognizer.destroy(); } catch (Exception e) {}
            androidRecognizer = null;
        }
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
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                Constants.PERM_CORE);
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
            }
        } else if (requestCode == Constants.PERM_SMS && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            // SMS 权限已就绪
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
    }

    private void setupVoiceButtonGradient() {
        btnMainBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{ContextCompat.getColor(this, R.color.grad_btn_start),
                        ContextCompat.getColor(this, R.color.grad_btn_end)});
        btnMainBg.setCornerRadius(1000f);

        // 延迟设置 — 等待 RadarFragment 视图创建完成后应用
        findViewById(android.R.id.content).post(() -> {
            View btnContainer = findViewById(R.id.btnContainerMain);
            if (btnContainer != null) btnContainer.setBackground(btnMainBg);
        });

        // 呼吸圆背景
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
        backgroundExecutor.execute(() -> {
            android.database.sqlite.SQLiteDatabase db = dbHelper.getWritableDatabase();
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(Constants.COL_TIME,
                    new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                            .format(new java.util.Date()));
            values.put(Constants.COL_TYPE, type);
            values.put(Constants.COL_DETAIL, detail);
            values.put(Constants.COL_POSITIVE, positive ? 1 : 0);
            db.insert(Constants.TABLE_RECORDS, null, values);
            db.close();
        });
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
                        + "摇晃手机或在设置中配置 SOS 紧急求助。")
                .setPositiveButton("开始体验", null)
                .setCancelable(false)
                .show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (breathingEngine != null) breathingEngine.stop();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (backgroundExecutor != null) backgroundExecutor.shutdown();
        if (faceLandmarker != null) faceLandmarker.close();
        if (androidRecognizer != null) { androidRecognizer.destroy(); }
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }
}
