package com.example.emoscope.fragments;

import android.Manifest;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.emoscope.Constants;
import com.example.emoscope.EmotionCalibrationProfile;
import com.example.emoscope.LocalDataManager;
import com.example.emoscope.MainActivity;
import com.example.emoscope.NotificationHelper;
import com.example.emoscope.PersonalProfile;
import com.example.emoscope.R;
import com.example.emoscope.SecureStorage;
import com.example.emoscope.viewmodels.RadarViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

public class SettingsFragment extends Fragment {

    private SharedPreferences prefs;
    private SecureStorage secureStorage;

    private TextView tvCurrentContact;
    private TextView tvCurrentSensitivity;
    private TextView tvCurrentApiKey;
    private TextView tvCurrentGoal;
    private TextView tvProfileSummary;
    private TextView tvNotifyTime;
    private MaterialSwitch switchHaptic;
    private MaterialSwitch switchTts;
    private MaterialSwitch switchBiometric;
    private MaterialSwitch switchNotifyDaily;
    private MaterialSwitch switchNotifyWeekly;
    private MaterialSwitch switchVoiceMode;
    private MaterialSwitch switchPrivacy;
    private View btnNotifyTime;
    private View btnSetContact;
    private View btnSetSensitivity;
    private View btnSetApiKey;
    private View btnPrivacyCenter;
    private View btnClearLocalData;
    private View btnFocusGoal;
    private View btnPersonalProfile;

    private boolean isRestoringUI = false;
    private boolean isBiometricEnabled = false;
    private boolean isTtsEnabled;
    private boolean isHapticEnabled;
    private float shakeThreshold;
    private String emergencyContact;
    private String deepseekApiKey;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        secureStorage = new SecureStorage(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        loadPreferences();
        updateUI();
        setupInteractions();
    }

    private void bindViews(View v) {
        tvCurrentContact = v.findViewById(R.id.tvCurrentContact);
        tvCurrentSensitivity = v.findViewById(R.id.tvCurrentSensitivity);
        tvCurrentApiKey = v.findViewById(R.id.tvCurrentApiKey);
        tvCurrentGoal = v.findViewById(R.id.tvCurrentGoal);
        tvProfileSummary = v.findViewById(R.id.tvProfileSummary);
        tvNotifyTime = v.findViewById(R.id.tvNotifyTime);
        switchHaptic = v.findViewById(R.id.switchHaptic);
        switchTts = v.findViewById(R.id.switchTts);
        switchBiometric = v.findViewById(R.id.switchBiometric);
        switchNotifyDaily = v.findViewById(R.id.switchNotifyDaily);
        switchNotifyWeekly = v.findViewById(R.id.switchNotifyWeekly);
        switchVoiceMode = v.findViewById(R.id.switchVoiceMode);
        switchPrivacy = v.findViewById(R.id.switchPrivacy);
        btnNotifyTime = v.findViewById(R.id.btnNotifyTime);
        btnSetContact = v.findViewById(R.id.btnSetContact);
        btnSetSensitivity = v.findViewById(R.id.btnSetSensitivity);
        btnSetApiKey = v.findViewById(R.id.btnSetApiKey);
        btnPrivacyCenter = v.findViewById(R.id.btnPrivacyCenter);
        btnClearLocalData = v.findViewById(R.id.btnClearLocalData);
        btnFocusGoal = v.findViewById(R.id.btnFocusGoal);
        btnPersonalProfile = v.findViewById(R.id.btnPersonalProfile);
    }

    private void loadPreferences() {
        deepseekApiKey = secureStorage.get(Constants.KEY_API_KEY, Constants.DEFAULT_API_KEY);
        emergencyContact = secureStorage.get(Constants.KEY_CONTACT, Constants.DEFAULT_CONTACT);
        isTtsEnabled = prefs.getBoolean(Constants.KEY_TTS, Constants.DEFAULT_TTS);
        isHapticEnabled = prefs.getBoolean(Constants.KEY_HAPTIC, Constants.DEFAULT_HAPTIC);
        shakeThreshold = prefs.getFloat(Constants.KEY_SHAKE_THRESH, Constants.DEFAULT_SHAKE_THRESHOLD);
        isBiometricEnabled = prefs.getBoolean(Constants.KEY_BIOMETRIC, false);
    }

    private void updateUI() {
        isRestoringUI = true;
        tvCurrentContact.setText(emergencyContact.isEmpty() ? "未设置" : emergencyContact);
        switchTts.setChecked(isTtsEnabled);
        switchHaptic.setChecked(isHapticEnabled);

        if (shakeThreshold > 3.0f) {
            tvCurrentSensitivity.setText("低");
        } else if (shakeThreshold < 2.0f) {
            tvCurrentSensitivity.setText("高");
        } else {
            tvCurrentSensitivity.setText("中等");
        }

        if (deepseekApiKey.length() > 10) {
            tvCurrentApiKey.setText(deepseekApiKey.substring(0, 5) + "..."
                    + deepseekApiKey.substring(deepseekApiKey.length() - 4));
        } else {
            tvCurrentApiKey.setText("未配置");
        }
        if (tvCurrentGoal != null) {
            tvCurrentGoal.setText(prefs.getString(
                    Constants.KEY_FOCUS_GOAL, Constants.DEFAULT_FOCUS_GOAL));
        }
        if (tvProfileSummary != null) {
            tvProfileSummary.setText(loadPersonalProfile().summary());
        }

        switchNotifyDaily.setChecked(prefs.getBoolean(Constants.KEY_NOTIFY_DAILY, false));
        switchNotifyWeekly.setChecked(prefs.getBoolean(Constants.KEY_NOTIFY_WEEKLY, false));
        int h = prefs.getInt(Constants.KEY_NOTIFY_HOUR, Constants.DEFAULT_NOTIFY_HOUR);
        int m = prefs.getInt(Constants.KEY_NOTIFY_MINUTE, Constants.DEFAULT_NOTIFY_MINUTE);
        tvNotifyTime.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m));
        switchBiometric.setChecked(isBiometricEnabled);
        switchVoiceMode.setChecked(prefs.getBoolean(
                Constants.KEY_VOICE_CLICK_MODE, Constants.DEFAULT_VOICE_CLICK_MODE));
        switchPrivacy.setChecked(prefs.getBoolean(
                Constants.KEY_PRIVACY_MODE, Constants.DEFAULT_PRIVACY_MODE));
        isRestoringUI = false;

        syncTtsIcon();
    }

    private void syncTtsIcon() {
        RadarViewModel rvm = new ViewModelProvider(requireActivity()).get(RadarViewModel.class);
        rvm.setTtsIcon(isTtsEnabled ? R.drawable.ic_tts_on : R.drawable.ic_tts_off);
    }

    private void setupInteractions() {
        btnPrivacyCenter.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showPrivacyCenter();
        });

        btnClearLocalData.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            confirmClearLocalData();
        });

        if (btnFocusGoal != null) {
            btnFocusGoal.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                showFocusGoalDialog();
            });
        }

        if (btnPersonalProfile != null) {
            btnPersonalProfile.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                showPersonalProfileDialog();
            });
        }

        switchHaptic.setOnCheckedChangeListener((btn, checked) -> {
            if (isRestoringUI) return;
            isHapticEnabled = checked;
            prefs.edit().putBoolean(Constants.KEY_HAPTIC, checked).apply();
            if (checked) btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        });

        switchTts.setOnCheckedChangeListener((btn, checked) -> {
            if (isRestoringUI) return;
            isTtsEnabled = checked;
            prefs.edit().putBoolean(Constants.KEY_TTS, isTtsEnabled).apply();
            syncTtsIcon();
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onTtsSettingChanged(isTtsEnabled);
            }
        });

        switchBiometric.setOnCheckedChangeListener((btn, checked) -> {
            if (isRestoringUI) return;
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            KeyguardManager km = (KeyguardManager) requireContext()
                    .getSystemService(Context.KEYGUARD_SERVICE);
            if (checked && (km == null || !km.isKeyguardSecure())) {
                switchBiometric.setChecked(false);
                showSnackbar("此设备未设置系统锁屏，暂不能开启应用锁");
                return;
            }
            isBiometricEnabled = checked;
            prefs.edit().putBoolean(Constants.KEY_BIOMETRIC, checked).apply();
            showSnackbar(checked ? "应用锁已开启" : "应用锁已关闭");
        });

        switchVoiceMode.setOnCheckedChangeListener((btn, checked) -> {
            if (isRestoringUI) return;
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            prefs.edit().putBoolean(Constants.KEY_VOICE_CLICK_MODE, checked).apply();
            showSnackbar(checked ? "点击切换模式已开启" : "已切换为长按录音模式");
        });

        switchPrivacy.setOnCheckedChangeListener((btn, checked) -> {
            if (isRestoringUI) return;
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            prefs.edit().putBoolean(Constants.KEY_PRIVACY_MODE, checked).apply();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onPrivacyModeChanged();
            }
            showSnackbar(checked ? "隐私模式已开启" : "隐私模式已关闭");
        });

        setupNotificationControls();
        setupEditableRows();
    }

    private void setupNotificationControls() {
        switchNotifyDaily.setOnCheckedChangeListener((btn, checked) -> {
            if (isRestoringUI) return;
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            prefs.edit().putBoolean(Constants.KEY_NOTIFY_DAILY, checked).apply();
            if (checked) {
                requestNotificationPermissionIfNeeded();
                checkExactAlarmPermission();
                int hour = prefs.getInt(Constants.KEY_NOTIFY_HOUR, Constants.DEFAULT_NOTIFY_HOUR);
                int minute = prefs.getInt(Constants.KEY_NOTIFY_MINUTE, Constants.DEFAULT_NOTIFY_MINUTE);
                NotificationHelper.scheduleDailyReminder(requireContext(), hour, minute);
                showSnackbar(String.format(Locale.getDefault(),
                        "每日提醒已开启（%02d:%02d）", hour, minute));
            } else {
                NotificationHelper.cancelDailyReminder(requireContext());
                showSnackbar("每日提醒已关闭");
            }
        });

        switchNotifyWeekly.setOnCheckedChangeListener((btn, checked) -> {
            if (isRestoringUI) return;
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            prefs.edit().putBoolean(Constants.KEY_NOTIFY_WEEKLY, checked).apply();
            if (checked) {
                checkExactAlarmPermission();
                NotificationHelper.scheduleWeeklySummary(requireContext());
                showSnackbar("每周报告已开启");
            } else {
                NotificationHelper.cancelWeeklySummary(requireContext());
                showSnackbar("每周报告已关闭");
            }
        });

        btnNotifyTime.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            int h = prefs.getInt(Constants.KEY_NOTIFY_HOUR, Constants.DEFAULT_NOTIFY_HOUR);
            int m = prefs.getInt(Constants.KEY_NOTIFY_MINUTE, Constants.DEFAULT_NOTIFY_MINUTE);
            new TimePickerDialog(requireContext(), (view, hour, minute) -> {
                prefs.edit().putInt(Constants.KEY_NOTIFY_HOUR, hour)
                        .putInt(Constants.KEY_NOTIFY_MINUTE, minute).apply();
                tvNotifyTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
                if (switchNotifyDaily.isChecked()) {
                    NotificationHelper.scheduleDailyReminder(requireContext(), hour, minute);
                }
            }, h, m, true).show();
        });
    }

    private void setupEditableRows() {
        btnSetContact.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            EditText input = new EditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_PHONE);
            input.setText(emergencyContact);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("设置紧急联系人")
                    .setMessage("SOS 会在你确认后向此号码发送求助短信。")
                    .setView(input)
                    .setPositiveButton("保存", (d, w) -> {
                        emergencyContact = input.getText().toString().trim();
                        secureStorage.put(Constants.KEY_CONTACT, emergencyContact);
                        updateUI();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        btnSetApiKey.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            EditText input = new EditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            input.setText(deepseekApiKey);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("配置 DeepSeek API Key")
                    .setMessage("API Key 会加密保存在本机。使用 AI 解读时，文本可能发送到 DeepSeek 服务。")
                    .setView(input)
                    .setPositiveButton("确认", (d, w) -> {
                        deepseekApiKey = input.getText().toString().trim();
                        secureStorage.put(Constants.KEY_API_KEY, deepseekApiKey);
                        updateUI();
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).onApiKeyChanged(deepseekApiKey);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        btnSetSensitivity.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            String[] options = {"高：更容易触发 SOS", "中等：推荐", "低：更少误触"};
            int checked = shakeThreshold < 2.0f ? 0 : (shakeThreshold > 3.0f ? 2 : 1);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("应激阻断灵敏度")
                    .setSingleChoiceItems(options, checked, (dialog, which) -> {
                        if (which == 0) shakeThreshold = 1.5f;
                        else if (which == 1) shakeThreshold = 2.5f;
                        else shakeThreshold = 3.5f;
                        prefs.edit().putFloat(Constants.KEY_SHAKE_THRESH, shakeThreshold).apply();
                        updateUI();
                        dialog.dismiss();
                    })
                    .show();
        });
    }

    private void showPrivacyCenter() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("隐私与安全中心")
                .setMessage("心镜是情绪记录和自助支持工具，不提供医疗诊断，也不能替代医生、心理咨询师或紧急救援。\n\n"
                        + "相机：仅用于本地面部情绪分析，当前版本不上传相机画面。\n\n"
                        + "麦克风：用于 Android 系统语音识别，把你的语音转成文本输入。\n\n"
                        + "短信：仅在 SOS 流程中向你设置的紧急联系人发送求助短信。\n\n"
                        + "AI 解读：当你使用 AI 功能时，输入文本和必要上下文可能发送到 DeepSeek 服务。\n\n"
                        + "本机数据：情绪记录保存在本地数据库；API Key 和紧急联系人通过 Android Keystore 加密保存。你可以随时清除本机数据。\n\n"
                        + "模型边界：表情和语音结果只是自我观察线索，不等同于心理诊断。光线、遮挡、语音识别误差和个人表达习惯都会影响结果。")
                .setNeutralButton("情绪校准", (dialog, which) -> showCalibrationDialog())
                .setPositiveButton("我知道了", null)
                .show();
    }

    private PersonalProfile loadPersonalProfile() {
        return new PersonalProfile(
                prefs.getString(Constants.KEY_PROFILE_NAME, ""),
                prefs.getString(Constants.KEY_PROFILE_IDENTITY, ""),
                prefs.getString(Constants.KEY_FOCUS_GOAL, Constants.DEFAULT_FOCUS_GOAL),
                prefs.getString(Constants.KEY_PROFILE_EMOTION_PREF, ""));
    }

    private void showPersonalProfileDialog() {
        PersonalProfile profile = loadPersonalProfile();
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, 8, pad, 0);

        EditText nameInput = new EditText(requireContext());
        nameInput.setHint("昵称，例如 Ziyang");
        nameInput.setSingleLine(true);
        nameInput.setText(profile.hasCustomName() ? profile.displayName() : "");
        form.addView(nameInput);

        EditText identityInput = new EditText(requireContext());
        identityInput.setHint("身份标签，例如 学生 / 创作者");
        identityInput.setSingleLine(true);
        identityInput.setText("私人情绪档案".equals(profile.identityLabel())
                ? "" : profile.identityLabel());
        form.addView(identityInput);

        EditText emotionInput = new EditText(requireContext());
        emotionInput.setHint("偏好的情绪表达，例如 平静 / 专注");
        emotionInput.setSingleLine(true);
        emotionInput.setText(profile.emotionPreference());
        form.addView(emotionInput);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑私人档案")
                .setMessage("这些信息只保存在本机，用于私人化显示和提示，不用于诊断。")
                .setView(form)
                .setPositiveButton("保存", (dialog, which) -> {
                    prefs.edit()
                            .putString(Constants.KEY_PROFILE_NAME,
                                    nameInput.getText().toString().trim())
                            .putString(Constants.KEY_PROFILE_IDENTITY,
                                    identityInput.getText().toString().trim())
                            .putString(Constants.KEY_PROFILE_EMOTION_PREF,
                                    emotionInput.getText().toString().trim())
                            .apply();
                    updateUI();
                    showSnackbar("私人档案已保存在本机");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCalibrationDialog() {
        String[] options = new String[Constants.EMOTION_NAMES.length + 1];
        options[0] = "不使用校准";
        for (int i = 0; i < Constants.EMOTION_NAMES.length; i++) {
            options[i + 1] = "我的 " + Constants.EMOTION_NAMES[i] + " 表情偏弱/偏难识别";
        }
        EmotionCalibrationProfile current = EmotionCalibrationProfile.fromStorageString(
                prefs.getString(Constants.KEY_EMOTION_CALIBRATION, ""));
        int checked = current.isEnabled() ? current.getTargetIndex() + 1 : 0;
        final int[] selected = {checked};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("表情个性化校准")
                .setMessage("选择后，系统会对该类表情做轻量补偿；这只是减少工程误差，不会改变“非诊断工具”的边界。")
                .setSingleChoiceItems(options, checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = "";
                    if (selected[0] > 0) {
                        value = EmotionCalibrationProfile
                                .fromTargetIndex(selected[0] - 1)
                                .toStorageString();
                    }
                    prefs.edit().putString(Constants.KEY_EMOTION_CALIBRATION, value).apply();
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).onEmotionCalibrationChanged(value);
                    }
                    showSnackbar("情绪校准已保存");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showFocusGoalDialog() {
        String[] goals = {"建立记录习惯", "减压", "睡眠前整理", "识别低落周期"};
        String current = prefs.getString(Constants.KEY_FOCUS_GOAL, Constants.DEFAULT_FOCUS_GOAL);
        int checked = 0;
        for (int i = 0; i < goals.length; i++) {
            if (goals[i].equals(current)) {
                checked = i;
                break;
            }
        }
        final int[] selected = {checked};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("关注目标")
                .setSingleChoiceItems(goals, checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton("保存", (dialog, which) -> {
                    prefs.edit().putString(Constants.KEY_FOCUS_GOAL, goals[selected[0]]).apply();
                    updateUI();
                    showSnackbar("已更新目标：" + goals[selected[0]]);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmClearLocalData() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("清除本机数据？")
                .setMessage("这会删除本机情绪记录、偏好设置、提醒计划、API Key 和紧急联系人。操作完成后无法恢复。")
                .setPositiveButton("确认清除", (dialog, which) -> clearLocalData())
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearLocalData() {
        Context appContext = requireContext().getApplicationContext();
        LocalDataManager.clearAllLocalData(appContext);

        prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        secureStorage = new SecureStorage(requireContext());
        loadPreferences();
        updateUI();

        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.onApiKeyChanged(Constants.DEFAULT_API_KEY);
            activity.onTtsSettingChanged(Constants.DEFAULT_TTS);
            activity.onPrivacyModeChanged();
            activity.onEmotionCalibrationChanged("");
        }
        showSnackbar("本机数据已清除");
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    Constants.PERM_NOTIFY);
        }
    }

    private void checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("需要精确闹钟权限")
                        .setMessage("定时提醒需要系统允许心镜使用闹钟和提醒权限。")
                        .setPositiveButton("去设置", (d, w) -> {
                            Intent intent = new Intent(
                                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            intent.setData(android.net.Uri.parse("package:"
                                    + requireContext().getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        }
    }

    private void showSnackbar(String msg) {
        View v = getView();
        if (v != null) Snackbar.make(v, msg, Snackbar.LENGTH_SHORT).show();
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public String getApiKey() {
        return deepseekApiKey;
    }

    public boolean isTtsEnabled() {
        return isTtsEnabled;
    }

    public boolean isHapticEnabled() {
        return isHapticEnabled;
    }

    public float getShakeThreshold() {
        return shakeThreshold;
    }

    public boolean isBiometricEnabled() {
        return isBiometricEnabled;
    }

    public void refreshUI() {
        loadPreferences();
        updateUI();
    }
}
