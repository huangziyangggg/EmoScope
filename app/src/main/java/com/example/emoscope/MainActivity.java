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
import com.example.emoscope.controllers.CameraOverlayCoordinator;
import com.example.emoscope.controllers.CameraSessionPolicy;
import com.example.emoscope.controllers.FaceCaptureRecord;
import com.example.emoscope.controllers.FaceCapturePersistenceController;
import com.example.emoscope.controllers.FaceCaptureScorePolicy;
import com.example.emoscope.controllers.SosOverlayCoordinator;
import com.example.emoscope.viewmodels.HistoryViewModel;
import com.example.emoscope.viewmodels.RadarViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
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
    private FaceCapturePersistenceController faceCapturePersistence;
    private BreathingEngine breathingEngine;
    private BreathingHapticController breathingHaptic;
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
    private View layoutBreathing, layoutCameraMode, layoutVoiceMode;
    private PreviewView viewFinder;
    private ImageView tvCameraFaceEmoji;
    private TextView tvCameraFaceState, tvCameraProb1, tvCameraProb2, tvCameraProb3;
    private TextView tvBreathText;
    private View breathCircle, btnCloseBreath, btnCallHotline;
    private BreathingOverlayView breathOverlay;
    // 实验性 rPPG
    private View llRppgDisplay;
    private TextView tvRppgBpm, tvRppgQuality;

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
    private View btnCloseVoice, btnVoiceStartStop;
    private TextView tvVoiceStatus, tvVoiceTranscript, tvVoiceHint, tvVoiceAction;

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
        CrashHandler.install(this);
        setContentView(R.layout.activity_main);
        // 处理系统栏内边距，避免内容被状态栏遮挡
        View fragmentContainer = findViewById(R.id.fragmentContainer);
        int baseContentPaddingTop = fragmentContainer.getPaddingTop();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer, (v, insets) -> {
            int top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), baseContentPaddingTop + top,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        View bottomNavigation = findViewById(R.id.bottomNav);
        int baseNavigationHeight = bottomNavigation.getLayoutParams().height;
        int baseNavigationBottomPadding = bottomNavigation.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation, (v, insets) -> {
            int navigationInset = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
            android.view.ViewGroup.LayoutParams params = v.getLayoutParams();
            int targetHeight = BottomNavigationInsetsPolicy.containerHeight(
                    baseNavigationHeight, navigationInset);
            if (params.height != targetHeight) {
                params.height = targetHeight;
                v.setLayoutParams(params);
            }
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    BottomNavigationInsetsPolicy.bottomPadding(baseNavigationBottomPadding, navigationInset));
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
                currentFaceTop3Desc = r.cameraState + " | " + r.confidenceMessage;
                radarVM.setFaceResult(R.drawable.ic_face_scan,
                        r.prob1, r.prob2, r.prob3,
                        R.drawable.ic_emotion_calm, r.moodLabel + " · " + r.confidenceMessage);
                tvCameraFaceEmoji.setImageResource(R.drawable.ic_face_scan);
                tvCameraFaceState.setText(r.cameraState + "  |  " + r.moodLabel
                        + "  |  " + r.confidenceMessage);
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
        faceAnalyzer.setCalibrationProfile(EmotionCalibrationProfile.fromStorageString(
                getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                        .getString(Constants.KEY_EMOTION_CALIBRATION, "")));

        // 绑定 Activity 级别视图
        bindActivityViews();

        faceCapturePersistence = new FaceCapturePersistenceController(
                backgroundExecutor, dbHelper::saveRecord, record -> runOnUiThread(() -> {
            showUserMessage(record.successMessage());
            resetCaptureUI();
            if (radarFragment != null) {
                radarFragment.refreshDailyLoop();
            }
        }));

        cameraController = new CameraEmotionController(this, this, viewFinder,
                backgroundExecutor, new CameraEmotionController.Callback() {
                    @Override public void onLightState(int iconRes, String description, int luminance) {
                        runOnUiThread(() -> {
                            currentLightDesc = description;
                            faceAnalyzer.setAmbientLuminance(luminance);
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

                    @Override public void onRppgUpdate(RppgAnalyzer.RppgResult result) {
                        runOnUiThread(() -> updateRppgDisplay(result));
                    }
                });

        // 呼吸引擎
        breathingHaptic = new BreathingHapticController(this);
        breathingEngine = new BreathingEngine(breathCircle, new BreathingEngine.BreathCallback() {
            @Override public void onPhaseChange(String text) { tvBreathText.setText(text); }
            @Override
            public void onPhaseStart(int phaseIndex, long durationMs, String phaseText) {
                // 呼吸触觉引导 — 仿 Apple Watch 节奏
                if (breathingHaptic != null) {
                    breathingHaptic.playPhase(phaseIndex, durationMs);
                }
            }
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
                runOnUiThread(() -> {
                    radarVM.setVoiceListening();
                    if (tvVoiceStatus != null) tvVoiceStatus.setText("正在聆听…");
                    if (tvVoiceTranscript != null) tvVoiceTranscript.setText("请自然说出此刻的感受。");
                });
            }

            @Override public void onProcessing() {
                runOnUiThread(() -> {
                    radarVM.setVoiceButtonText("处理中...");
                    if (tvVoiceStatus != null) tvVoiceStatus.setText("正在整理你的表达…");
                    if (tvVoiceHint != null) tvVoiceHint.setText("说完后点按按钮结束，表达会被记录下来。");
                });
            }

            @Override public void onPartialText(String text) {
                runOnUiThread(() -> {
                    radarVM.setVoiceText("\"" + text + "\"");
                    if (tvVoiceTranscript != null) tvVoiceTranscript.setText(text);
                });
            }

            @Override public void onFinalText(String text, VoiceFeatureAnalyzer.Result features) {
                runOnUiThread(() -> handleVoiceResult(text, features));
            }

            @Override public void onNoSpeech() {
                runOnUiThread(() -> {
                    stopRecording();
                    radarVM.setVoiceNotHeard();
                    if (tvVoiceStatus != null) tvVoiceStatus.setText("没有听清");
                    if (tvVoiceTranscript != null) tvVoiceTranscript.setText("没有检测到清晰语音，可以靠近一点再试。");
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    stopRecording();
                    radarVM.setVoiceText(message);
                    if (tvVoiceStatus != null) tvVoiceStatus.setText("语音识别失败");
                    if (tvVoiceTranscript != null) tvVoiceTranscript.setText(message);
                    if (tvVoiceHint != null) tvVoiceHint.setText("如果反复失败，请检查系统语音服务、网络和麦克风权限。");
                    showUserMessage(message);
                });
            }
        });

        // 初始化 Fragment
        initFragments();

        // 返回键：优先关闭覆盖层，再退出 App
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (layoutCameraMode != null && layoutCameraMode.getVisibility() == View.VISIBLE) {
                    closeCameraOverlay();
                } else if (layoutVoiceMode != null && layoutVoiceMode.getVisibility() == View.VISIBLE) {
                    closeVoiceOverlay();
                } else if (layoutBreathing != null && layoutBreathing.getVisibility() == View.VISIBLE) {
                    sosController.stopBreathingIntervention();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // 设置导航
        setupBottomNav();

        // 设置叠加层交互
        setupOverlayInteractions();

        setupBreathingCircleBackground();

        // 首次启动引导
        showFirstLaunchGuide();

        // 通知渠道
        NotificationHelper.createChannels(this);

        // 相机和麦克风均在用户主动进入对应功能时再申请，手动记录无需授权。
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
            switch (MainNavigationPolicy.destinationFor(id, R.id.navRadar, R.id.navWorkshop,
                    R.id.navHistory, R.id.navSettings)) {
                case HOME:
                    target = radarFragment;
                    break;
                case GROWTH:
                    target = workshopFragment;
                    workshopFragment.refreshUI();
                    break;
                case HISTORY:
                    target = historyFragment;
                    historyFragment.loadHistoryData();
                    break;
                case SETTINGS:
                default:
                    target = settingsFragment;
                    settingsFragment.refreshUI();
                    break;
            }

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
        if (!checkCameraPermission()) {
            showCameraPermissionDialog();
            return;
        }
        latestEmotionResult = null;
        cardFaceResult.setVisibility(View.GONE);
        if (llRppgDisplay != null) llRppgDisplay.setVisibility(View.GONE);
        fragmentContainer().setVisibility(View.GONE);
        bottomNav.setVisibility(View.GONE);
        layoutCameraMode.setAlpha(0f);
        layoutCameraMode.setVisibility(View.VISIBLE);
        layoutCameraMode.animate().alpha(1f).setDuration(300).start();
        startRequestedCameraSession();
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

    private void openVoiceOverlay() {
        triggerHaptic(getWindow().getDecorView(), HapticFeedbackConstants.VIRTUAL_KEY);
        fragmentContainer().setVisibility(View.GONE);
        bottomNav.setVisibility(View.GONE);
        if (tvVoiceStatus != null) {
            tvVoiceStatus.setText("点按下方按钮，说出此刻的感受。");
        }
        if (tvVoiceTranscript != null) {
            tvVoiceTranscript.setText("还没有开始。");
        }
        if (tvVoiceHint != null) {
            tvVoiceHint.setText("识别完成后，如果你已配置 AI Key，可以获得一段温和回应。");
        }
        if (tvVoiceAction != null) {
            tvVoiceAction.setText("开始倾诉");
        }
        layoutVoiceMode.setAlpha(0f);
        layoutVoiceMode.setVisibility(View.VISIBLE);
        layoutVoiceMode.animate().alpha(1f).setDuration(260).start();
    }

    private void closeVoiceOverlay() {
        if (isVoiceRecording) {
            stopRecording();
        }
        layoutVoiceMode.animate().cancel();
        layoutVoiceMode.animate().alpha(0f).setDuration(220).withEndAction(() -> {
            layoutVoiceMode.setVisibility(View.GONE);
            layoutVoiceMode.setAlpha(1f);
            fragmentContainer().setVisibility(View.VISIBLE);
            bottomNav.setVisibility(View.VISIBLE);
        }).start();
    }

    @Override public void onVoiceButtonPressed() {
        openVoiceOverlay();
    }

    private void startVoicePageRecording() {
        RuntimePermissionPolicy.NextAction audioPermissionAction =
                RuntimePermissionPolicy.nextAction(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED,
                        ActivityCompat.shouldShowRequestPermissionRationale(
                                this, Manifest.permission.RECORD_AUDIO));
        if (audioPermissionAction != RuntimePermissionPolicy.NextAction.ALREADY_GRANTED) {
            if (tvVoiceStatus != null) tvVoiceStatus.setText("需要麦克风权限");
            if (tvVoiceTranscript != null) tvVoiceTranscript.setText("授权后才能把你的声音转成文字。");
            if (audioPermissionAction == RuntimePermissionPolicy.NextAction.SHOW_RATIONALE) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("需要麦克风权限")
                        .setMessage("语音倾诉需要访问麦克风，用于将你说的话转成文字。录音数据仅用于本次识别。")
                        .setPositiveButton("允许", (d, w) -> requestAudioPermission())
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                requestAudioPermission();
            }
            return;
        }

        if (voiceController != null && !voiceController.isAvailable()) {
            String message = "当前设备没有可用的语音识别服务，请安装或启用 Google App / Google 语音搜索后重试。";
            if (tvVoiceStatus != null) tvVoiceStatus.setText("语音服务暂不可用");
            if (tvVoiceTranscript != null) tvVoiceTranscript.setText(message);
            if (tvVoiceHint != null) tvVoiceHint.setText("这是系统语音服务缺失导致的，不是你的麦克风或说话方式问题。");
            showUserMessage(message);
            return;
        }

        isVoiceRecording = true;
        triggerHaptic(btnVoiceStartStop, HapticFeedbackConstants.VIRTUAL_KEY);
        voiceRecordStartTime = System.currentTimeMillis();
        startVoiceRecognition();
        animateVoiceButton(true);
        if (tts != null && tts.isSpeaking()) tts.stop();
        radarVM.setRecording(true);
        radarVM.setVoiceListening();
        if (tvVoiceStatus != null) tvVoiceStatus.setText("正在聆听…");
        if (tvVoiceTranscript != null) tvVoiceTranscript.setText("请自然说出此刻的感受。");
        if (tvVoiceHint != null) tvVoiceHint.setText("点按按钮可结束本次倾诉。");
        if (tvVoiceAction != null) tvVoiceAction.setText("结束倾诉");
        startWaveAnimation();
        startVoiceCountdown();
    }

    private void beginVoiceRecognitionFromVoicePage() {
        // 先检查录音权限 — 独立请求，不捆绑相机
        RuntimePermissionPolicy.NextAction audioPermissionAction =
                RuntimePermissionPolicy.nextAction(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED,
                        ActivityCompat.shouldShowRequestPermissionRationale(
                                this, Manifest.permission.RECORD_AUDIO));
        if (audioPermissionAction != RuntimePermissionPolicy.NextAction.ALREADY_GRANTED) {
            if (audioPermissionAction == RuntimePermissionPolicy.NextAction.SHOW_RATIONALE) {
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
        View container = layoutVoiceMode != null && layoutVoiceMode.getVisibility() == View.VISIBLE
                ? btnVoiceStartStop : findViewById(R.id.btnContainerMain);
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
            View voiceModeWave = findViewById(R.id.voiceModeWaveView);
            View voiceModeSonar = findViewById(R.id.voiceModeSonarRipple);
            if (wave instanceof VoiceWaveView) ((VoiceWaveView) wave).start();
            if (sonar instanceof SonarRippleView) ((SonarRippleView) sonar).start();
            if (voiceModeWave instanceof VoiceWaveView) ((VoiceWaveView) voiceModeWave).start();
            if (voiceModeSonar instanceof SonarRippleView) ((SonarRippleView) voiceModeSonar).start();
        });
    }

    private void stopWaveAnimation() {
        runOnUiThread(() -> {
            View wave = findViewById(R.id.voiceWaveView);
            View sonar = findViewById(R.id.sonarRipple);
            View voiceModeWave = findViewById(R.id.voiceModeWaveView);
            View voiceModeSonar = findViewById(R.id.voiceModeSonarRipple);
            if (wave instanceof VoiceWaveView) ((VoiceWaveView) wave).stop();
            if (sonar instanceof SonarRippleView) ((SonarRippleView) sonar).stop();
            if (voiceModeWave instanceof VoiceWaveView) ((VoiceWaveView) voiceModeWave).stop();
            if (voiceModeSonar instanceof SonarRippleView) ((SonarRippleView) voiceModeSonar).stop();
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
        MoodDialogHelper.showMoodPicker(this, false, false,
                getString(R.string.mood_picker_title), (index, label, tag, note) -> {
                    saveToDatabase("手动记录", "心情: " + label,
                            MoodSelectionPolicy.isPositiveMood(index));
                    updateStreakFromMain();
                    refreshDailyLoopSoon();
                    showUserMessage(getString(R.string.mood_picker_saved, label));
                });
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
        triggerHaptic(btnVoiceStartStop, HapticFeedbackConstants.VIRTUAL_KEY);
        stopVoiceRecognition();

        animateVoiceButton(false);
        radarVM.setRecording(false);
        if (tvVoiceStatus != null) tvVoiceStatus.setText("正在整理你的表达…");
        if (tvVoiceAction != null) tvVoiceAction.setText("开始倾诉");
    }

    // ═══════════════════════════════════════════════════════════════
    // 覆盖层交互 (Camera / Breathing)
    // ═══════════════════════════════════════════════════════════════
    private void setupOverlayInteractions() {
        new CameraOverlayCoordinator(new CameraOverlayCoordinator.Host() {
            @Override
            public void performActionHaptic(View source) {
                triggerHaptic(source, HapticFeedbackConstants.VIRTUAL_KEY);
            }

            @Override
            public void closeCameraOverlay() {
                MainActivity.this.closeCameraOverlay();
            }

            @Override
            public void flipCamera(View source) {
                source.animate().rotationBy(180).setDuration(300).start();
                if (cameraController != null) cameraController.flipCamera();
            }

            @Override
            public void captureFaceScore() {
                MainActivity.this.captureFaceScore();
            }

            @Override
            public void saveCaptureResult() {
                MainActivity.this.saveCaptureResult();
            }

            @Override
            public void discardCaptureResult() {
                MainActivity.this.discardCaptureResult();
            }
        }).bind(findViewById(R.id.btnCloseCamera), findViewById(R.id.btnFlipCamera),
                btnCaptureFace, btnSaveCapture, btnDiscardCapture);

        if (btnCloseVoice != null) {
            btnCloseVoice.setOnClickListener(v -> closeVoiceOverlay());
        }
        if (btnVoiceStartStop != null) {
            btnVoiceStartStop.setOnClickListener(v -> {
                if (isVoiceRecording) {
                    stopRecording();
                } else {
                    startVoicePageRecording();
                }
            });
        }

        new SosOverlayCoordinator(new SosOverlayCoordinator.Host() {
            @Override
            public void performActionHaptic(View source) {
                triggerHaptic(source, HapticFeedbackConstants.VIRTUAL_KEY);
            }

            @Override
            public void stopBreathingIntervention() {
                MainActivity.this.stopBreathingIntervention();
            }

            @Override
            public void dialHotline() {
                startActivity(new Intent(Intent.ACTION_DIAL,
                        Uri.parse("tel:" + Constants.HOTLINE_NUMBER)));
            }
        }).bind(btnCloseBreath, btnCallHotline);
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
        int displayScore = FaceCaptureScorePolicy.displayScore(r.weightedScore);
        tvCaptureScore.setText(String.valueOf(displayScore));
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

    private void closeCameraOverlay() {
        if (cameraController != null) cameraController.release();
        if (llRppgDisplay != null) llRppgDisplay.setVisibility(View.GONE);
        layoutCameraMode.animate().cancel();
        layoutCameraMode.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            layoutCameraMode.setVisibility(View.GONE);
            layoutCameraMode.setAlpha(1f);
            fragmentContainer().setVisibility(View.VISIBLE);
            bottomNav.setVisibility(View.VISIBLE);
        }).start();
    }

    private void saveCaptureResult() {
        FaceAnalyzer.EmotionResult r = latestEmotionResult;
        if (r == null) return;

        FaceCaptureRecord record = FaceCaptureRecord.create(r.weightedScore,
                r.prob1, r.prob2, r.prob3);

        faceCapturePersistence.save(record);
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
        if (breathingHaptic != null) breathingHaptic.stop();
        if (sosController != null) sosController.stopBreathingIntervention();
    }

    // ═══════════════════════════════════════════════════════════════
    // 设置变更回调 (供 SettingsFragment 调用)
    // ═══════════════════════════════════════════════════════════════
    public void onTtsSettingChanged(boolean enabled) { updateTtsState(enabled); }
    public void onApiKeyChanged(String newKey) { deepSeekClient.setApiKey(newKey); }
    public void onEmotionCalibrationChanged(String profileValue) {
        if (faceAnalyzer != null) {
            faceAnalyzer.setCalibrationProfile(
                    EmotionCalibrationProfile.fromStorageString(profileValue));
        }
        showUserMessage("表情校准已更新");
    }
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

    private void startRequestedCameraSession() {
        if (!CameraSessionPolicy.shouldStart(checkCameraPermission(), true)) {
            return;
        }
        setupVisualEngine();
        startCamera();
    }

    private void startVoiceRecognition() {
        if (voiceController != null) voiceController.start();
    }

    private void stopVoiceRecognition() {
        if (voiceController != null) voiceController.stop();
    }

    private void handleVoiceResult(String text, VoiceFeatureAnalyzer.Result features) {
        if (text.isEmpty()) {
            radarVM.setVoiceNotHeard();
            return;
        }
        if (features == null) {
            float durationSec = Math.max(0.5f,
                    (System.currentTimeMillis() - voiceRecordStartTime) / 1000f);
            features = VoiceFeatureAnalyzer.analyze(text, durationSec, 0f, 0f, 0);
        }

        radarVM.setVoiceResult("\"" + text + "\"",
                features.summary);
        // 温和语速解读 — 供 UI 展示，不发往 AI
        radarVM.setVoiceGentleHint(VoiceFeatureAnalyzer.gentleDescription(features));
        if (tvVoiceStatus != null) tvVoiceStatus.setText("已听见你的表达");
        if (tvVoiceTranscript != null) tvVoiceTranscript.setText(text);
        if (tvVoiceHint != null) tvVoiceHint.setText(VoiceFeatureAnalyzer.gentleDescription(features));
        if (tvVoiceAction != null) tvVoiceAction.setText("再说一次");
        deepSeekClient.call(currentFaceTop3Desc, text, features.summary, currentLightDesc);
    }


    // ═══════════════════════════════════════════════════════════════
    // 权限
    // ═══════════════════════════════════════════════════════════════
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                Constants.PERM_AUDIO);
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, Constants.PERM_CAMERA);
    }

    private void requestSmsPermission() {
        RuntimePermissionPolicy.NextAction smsPermissionAction = RuntimePermissionPolicy.nextAction(
                checkSmsPermission(), ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.SEND_SMS));
        if (smsPermissionAction == RuntimePermissionPolicy.NextAction.ALREADY_GRANTED) {
            return;
        }
        if (smsPermissionAction == RuntimePermissionPolicy.NextAction.SHOW_RATIONALE) {
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
        if (requestCode == Constants.PERM_AUDIO) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                showUserMessage("麦克风权限已开启，现在可以对语音按钮说话了");
            } else {
                showUserMessage("需要麦克风权限才能使用语音功能，请在系统设置中开启");
            }
        } else if (requestCode == Constants.PERM_CAMERA) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                onFaceCardClicked();
            } else {
                showUserMessage("未获得相机权限，你仍可使用手动记录和语音倾诉");
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
        layoutVoiceMode = findViewById(R.id.layoutVoiceMode);
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
        btnCloseVoice = findViewById(R.id.btnCloseVoice);
        btnVoiceStartStop = findViewById(R.id.btnVoiceStartStop);
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus);
        tvVoiceTranscript = findViewById(R.id.tvVoiceTranscript);
        tvVoiceHint = findViewById(R.id.tvVoiceHint);
        tvVoiceAction = findViewById(R.id.tvVoiceAction);
        // 实验性 rPPG
        llRppgDisplay = findViewById(R.id.llRppgDisplay);
        tvRppgBpm = findViewById(R.id.tvRppgBpm);
        tvRppgQuality = findViewById(R.id.tvRppgQuality);
    }

    /** 实验性 rPPG 心率显示更新 */
    private void updateRppgDisplay(RppgAnalyzer.RppgResult result) {
        if (llRppgDisplay == null || tvRppgBpm == null || tvRppgQuality == null) return;

        if (result.hasBpm() && result.confidence > 0.3f) {
            llRppgDisplay.setVisibility(View.VISIBLE);
            tvRppgBpm.setText(result.bpmText());
            if (result.confidence > 0.6f) {
                tvRppgQuality.setText("信号好");
                tvRppgQuality.setTextColor(0xFF10B981);
            } else {
                tvRppgQuality.setText("实验性");
                tvRppgQuality.setTextColor(0xFF71717A);
            }
        } else if (result.signalQuality > 20) {
            llRppgDisplay.setVisibility(View.VISIBLE);
            tvRppgBpm.setText("...");
            tvRppgQuality.setText("采集中");
            tvRppgQuality.setTextColor(0xFF71717A);
        }
        // signalQuality太低时保持隐藏，避免闪烁不靠谱的数字
    }

    private void showCameraPermissionDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("需要相机权限")
                .setMessage("相机仅用于本地面容状态分析，不会上传相机画面。不开启时，你仍可使用手动记录和语音倾诉。")
                .setPositiveButton("允许相机", (d, w) -> requestCameraPermission())
                .setNegativeButton("暂不授权", null)
                .show();
    }

    private void showUserMessage(String message) {
        if (message == null || message.trim().isEmpty() || isFinishing()) return;
        View rootView = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        if (bottomNav != null && bottomNav.getVisibility() == View.VISIBLE) {
            snackbar.setAnchorView(bottomNav);
        }
        snackbar.show();
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
        if (faceAnalyzer != null) {
            faceAnalyzer.setCalibrationProfile(EmotionCalibrationProfile.fromStorageString(
                    prefs.getString(Constants.KEY_EMOTION_CALIBRATION, "")));
        }
        // 同步呼吸触觉引导开关
        if (breathingHaptic != null) {
            breathingHaptic.setEnabled(
                    prefs.getBoolean(Constants.KEY_HAPTIC, Constants.DEFAULT_HAPTIC));
        }
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
                .setTitle(OnboardingNarrative.title())
                .setMessage(OnboardingNarrative.message())
                .setPositiveButton(OnboardingNarrative.primaryActionLabel(),
                        (dialog, which) -> showFocusGoalDialog())
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
        if (breathingHaptic != null) breathingHaptic.stop();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (backgroundExecutor != null) backgroundExecutor.shutdown();
        if (cameraController != null) cameraController.release();
        if (voiceController != null) voiceController.stop();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }
}
