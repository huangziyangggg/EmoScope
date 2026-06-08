package com.example.emoscope.fragments;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.emoscope.Constants;
import com.example.emoscope.MainActivity;
import com.example.emoscope.NotificationHelper;
import com.example.emoscope.R;
import com.example.emoscope.SecureStorage;
import com.example.emoscope.viewmodels.RadarViewModel;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

/**
 * 控制中枢页 — 所有设置项的 UI 与交互逻辑。
 * 直接操作 SharedPreferences / SecureStorage，无需额外 ViewModel。
 */
public class SettingsFragment extends Fragment {

    private SharedPreferences prefs;
    private SecureStorage secureStorage;

    // ── 视图 ──
    private TextView tvCurrentContact, tvCurrentSensitivity, tvCurrentApiKey;
    private MaterialSwitch switchHaptic, switchTts, switchBiometric;
    private MaterialSwitch switchNotifyDaily, switchNotifyWeekly;
    private MaterialSwitch switchVoiceMode, switchPrivacy;
    private TextView tvNotifyTime;
    private View btnNotifyTime, btnSetContact, btnSetSensitivity, btnSetApiKey;

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
        switchHaptic = v.findViewById(R.id.switchHaptic);
        switchTts = v.findViewById(R.id.switchTts);
        switchBiometric = v.findViewById(R.id.switchBiometric);
        switchNotifyDaily = v.findViewById(R.id.switchNotifyDaily);
        switchNotifyWeekly = v.findViewById(R.id.switchNotifyWeekly);
        tvNotifyTime = v.findViewById(R.id.tvNotifyTime);
        btnNotifyTime = v.findViewById(R.id.btnNotifyTime);
        btnSetContact = v.findViewById(R.id.btnSetContact);
        btnSetSensitivity = v.findViewById(R.id.btnSetSensitivity);
        btnSetApiKey = v.findViewById(R.id.btnSetApiKey);
        switchVoiceMode = v.findViewById(R.id.switchVoiceMode);
        switchPrivacy = v.findViewById(R.id.switchPrivacy);
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
        tvCurrentContact.setText(emergencyContact);
        switchTts.setChecked(isTtsEnabled);
        switchHaptic.setChecked(isHapticEnabled);

        if (shakeThreshold > 3.0f) tvCurrentSensitivity.setText(R.string.sensitivity_low);
        else if (shakeThreshold < 2.0f) tvCurrentSensitivity.setText(R.string.sensitivity_high);
        else tvCurrentSensitivity.setText(R.string.sensitivity_medium);

        if (deepseekApiKey.length() > 10) {
            tvCurrentApiKey.setText(deepseekApiKey.substring(0, 5) + "..."
                    + deepseekApiKey.substring(deepseekApiKey.length() - 4));
        } else {
            tvCurrentApiKey.setText(R.string.api_key_unset);
        }

        // 通知设置同步
        isRestoringUI = true;
        switchNotifyDaily.setChecked(prefs.getBoolean(Constants.KEY_NOTIFY_DAILY, false));
        switchNotifyWeekly.setChecked(prefs.getBoolean(Constants.KEY_NOTIFY_WEEKLY, false));
        isRestoringUI = false;
        int h = prefs.getInt(Constants.KEY_NOTIFY_HOUR, Constants.DEFAULT_NOTIFY_HOUR);
        int m = prefs.getInt(Constants.KEY_NOTIFY_MINUTE, Constants.DEFAULT_NOTIFY_MINUTE);
        tvNotifyTime.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m));

        switchBiometric.setChecked(isBiometricEnabled);

        // 语音模式 & 隐私
        switchVoiceMode.setChecked(prefs.getBoolean(Constants.KEY_VOICE_CLICK_MODE, Constants.DEFAULT_VOICE_CLICK_MODE));
        switchPrivacy.setChecked(prefs.getBoolean(Constants.KEY_PRIVACY_MODE, Constants.DEFAULT_PRIVACY_MODE));

        // 同步 TTS 图标到 RadarViewModel
        syncTtsIcon();
    }

    /** 将 TTS 状态同步到 RadarViewModel 以更新首页图标 */
    private void syncTtsIcon() {
        RadarViewModel rvm = new ViewModelProvider(requireActivity()).get(RadarViewModel.class);
        rvm.setTtsIcon(isTtsEnabled ? R.drawable.ic_tts_on : R.drawable.ic_tts_off);
    }

    // ═══════════════════════════════════════════════════════════════
    // 交互设置
    // ═══════════════════════════════════════════════════════════════
    private void setupInteractions() {
        // 触觉反馈
        switchHaptic.setOnCheckedChangeListener((btn, checked) -> {
            isHapticEnabled = checked;
            prefs.edit().putBoolean(Constants.KEY_HAPTIC, checked).apply();
            if (checked) btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        });

        // AI 语音播报
        switchTts.setOnCheckedChangeListener((btn, checked) -> {
            isTtsEnabled = checked;
            prefs.edit().putBoolean(Constants.KEY_TTS, isTtsEnabled).apply();
            syncTtsIcon();
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            // 通知 Activity 更新 TTS 状态
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onTtsSettingChanged(isTtsEnabled);
            }
        });

        // 生物识别
        switchBiometric.setOnCheckedChangeListener((btn, checked) -> {
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            KeyguardManager km = (KeyguardManager) requireContext()
                    .getSystemService(Context.KEYGUARD_SERVICE);
            if (checked && (km == null || !km.isKeyguardSecure())) {
                switchBiometric.setChecked(false);
                showSnackbar(getString(R.string.biometric_not_available));
                return;
            }
            isBiometricEnabled = checked;
            prefs.edit().putBoolean(Constants.KEY_BIOMETRIC, checked).apply();
            showSnackbar(checked ? getString(R.string.biometric_lock_enabled)
                    : getString(R.string.biometric_lock_disabled));
        });

        // 语音点击切换模式
        switchVoiceMode.setOnCheckedChangeListener((btn, checked) -> {
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            prefs.edit().putBoolean(Constants.KEY_VOICE_CLICK_MODE, checked).apply();
            showSnackbar(checked ? "点击切换模式已开启" : "已切换为长按录音模式");
        });

        // 隐私模式
        switchPrivacy.setOnCheckedChangeListener((btn, checked) -> {
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            prefs.edit().putBoolean(Constants.KEY_PRIVACY_MODE, checked).apply();
            showSnackbar(checked ? "隐私模式已开启" : "隐私模式已关闭");
        });

        // 每日提醒
        switchNotifyDaily.setOnCheckedChangeListener((btn, checked) -> {
            if (isRestoringUI) return;
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            prefs.edit().putBoolean(Constants.KEY_NOTIFY_DAILY, checked).apply();
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(requireActivity(),
                                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                Constants.PERM_NOTIFY);
                    }
                }
                checkExactAlarmPermission();
                int hour = prefs.getInt(Constants.KEY_NOTIFY_HOUR, Constants.DEFAULT_NOTIFY_HOUR);
                int minute = prefs.getInt(Constants.KEY_NOTIFY_MINUTE, Constants.DEFAULT_NOTIFY_MINUTE);
                NotificationHelper.scheduleDailyReminder(requireContext(), hour, minute);
                showSnackbar(String.format(Locale.getDefault(),
                        getString(R.string.notify_daily_on), hour, minute));
            } else {
                NotificationHelper.cancelDailyReminder(requireContext());
                showSnackbar(getString(R.string.notify_daily_off));
            }
        });

        // 每周报告
        switchNotifyWeekly.setOnCheckedChangeListener((btn, checked) -> {
            if (isRestoringUI) return;
            btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            prefs.edit().putBoolean(Constants.KEY_NOTIFY_WEEKLY, checked).apply();
            if (checked) {
                checkExactAlarmPermission();
                NotificationHelper.scheduleWeeklySummary(requireContext());
                showSnackbar(getString(R.string.notify_weekly_on));
            } else {
                NotificationHelper.cancelWeeklySummary(requireContext());
                showSnackbar(getString(R.string.notify_weekly_off));
            }
        });

        // 提醒时间
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

        // 紧急联系人
        btnSetContact.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            EditText input = new EditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_PHONE);
            input.setText(emergencyContact);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_contact_title)
                    .setView(input)
                    .setPositiveButton(R.string.dialog_save, (d, w) -> {
                        String val = input.getText().toString().trim();
                        if (!val.isEmpty()) {
                            emergencyContact = val;
                            secureStorage.put(Constants.KEY_CONTACT, val);
                            updateUI();
                        }
                    })
                    .setNegativeButton(getString(R.string.dialog_cancel), null).show();
        });

        // API Key
        btnSetApiKey.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            EditText input = new EditText(requireContext());
            input.setText(deepseekApiKey);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_api_title)
                    .setMessage(R.string.dialog_api_message)
                    .setView(input)
                    .setPositiveButton(R.string.dialog_bind, (d, w) -> {
                        String val = input.getText().toString().trim();
                        if (!val.isEmpty()) {
                            deepseekApiKey = val;
                            secureStorage.put(Constants.KEY_API_KEY, val);
                            updateUI();
                            // 通知 Activity 更新 API Key
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).onApiKeyChanged(val);
                            }
                        }
                    })
                    .setNegativeButton(getString(R.string.dialog_cancel), null).show();
        });

        // 灵敏度
        btnSetSensitivity.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            String[] options = {getString(R.string.dialog_sensitivity_opt_high),
                    getString(R.string.dialog_sensitivity_opt_medium),
                    getString(R.string.dialog_sensitivity_opt_low)};
            int checked = shakeThreshold < 2.0f ? 0 : (shakeThreshold > 3.0f ? 2 : 1);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_sensitivity_title)
                    .setSingleChoiceItems(options, checked, (dialog, which) -> {
                        if (which == 0) shakeThreshold = 1.5f;
                        else if (which == 1) shakeThreshold = 2.5f;
                        else if (which == 2) shakeThreshold = 3.5f;
                        prefs.edit().putFloat(Constants.KEY_SHAKE_THRESH, shakeThreshold).apply();
                        updateUI();
                        dialog.dismiss();
                    }).show();
        });
    }

    private void checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("需要精确闹钟权限")
                        .setMessage("定时提醒需要「闹钟和提醒」权限才能准时触发。\n\n"
                                + "请在系统设置中允许 EmoScope 的此权限。")
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

    // ═══════════════════════════════════════════════════════════════
    // 公开 getter — 供 Activity 读取当前设置
    // ═══════════════════════════════════════════════════════════════
    public String getEmergencyContact() { return emergencyContact; }
    public String getApiKey() { return deepseekApiKey; }
    public boolean isTtsEnabled() { return isTtsEnabled; }
    public boolean isHapticEnabled() { return isHapticEnabled; }
    public float getShakeThreshold() { return shakeThreshold; }
    public boolean isBiometricEnabled() { return isBiometricEnabled; }

    /** Activity 调用以在 view 创建后恢复 UI 状态 */
    public void refreshUI() {
        loadPreferences();
        updateUI();
    }
}
