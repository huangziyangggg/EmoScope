package com.example.emoscope;

import android.content.Context;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Coordinates SOS countdown, emergency SMS throttling, and breathing intervention UI.
 */
public class SosInterventionController {

    public interface Host {
        void triggerHaptic(View view, int feedbackConstant);
        boolean checkSmsPermission();
        void requestSmsPermission();
        String emergencyContact();
        void setSosVisible(boolean visible);
        void showMessage(String message);
    }

    private final AppCompatActivity activity;
    private final BreathingEngine breathingEngine;
    private final View layoutBreathing;
    private final BreathingOverlayView breathOverlay;
    private final Host host;

    private boolean hasSentSmsThisSession = false;
    private long lastSmsTime = 0;
    private int breathMode = Constants.BREATH_MODE_BOX;

    public SosInterventionController(AppCompatActivity activity,
                                     BreathingEngine breathingEngine,
                                     View layoutBreathing,
                                     BreathingOverlayView breathOverlay,
                                     Host host) {
        this.activity = activity;
        this.breathingEngine = breathingEngine;
        this.layoutBreathing = layoutBreathing;
        this.breathOverlay = breathOverlay;
        this.host = host;
    }

    public void showCountdown() {
        final int[] count = {3};
        final android.os.Handler handler = new android.os.Handler(activity.getMainLooper());
        final TextView msgView = new TextView(activity);
        msgView.setPadding(48, 32, 48, 16);
        msgView.setTextSize(16);
        msgView.setText(String.format(activity.getString(R.string.sos_countdown_message), 3));

        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.sos_countdown_title))
                .setView(msgView)
                .setNegativeButton(activity.getString(R.string.sos_cancel_now), (d, w) -> {
                    count[0] = -1;
                })
                .setCancelable(false)
                .create();

        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (count[0] < 0) return;
                if (count[0] == 0) {
                    if (dialog.isShowing()) dialog.dismiss();
                    showBreathModeDialog();
                    return;
                }
                msgView.setText(String.format(
                        activity.getString(R.string.sos_countdown_message), count[0]));
                count[0]--;
                handler.postDelayed(this, 1000);
            }
        };
        dialog.show();
        handler.postDelayed(tick, 0);
    }

    public void showBreathModeDialog() {
        android.content.SharedPreferences prefs = activity.getSharedPreferences(
                Constants.PREFS_NAME, Context.MODE_PRIVATE);
        breathMode = prefs.getInt(Constants.KEY_BREATH_MODE, Constants.BREATH_MODE_BOX);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.breath_mode_title))
                .setSingleChoiceItems(Constants.BREATH_MODE_NAMES, breathMode, (dialog, which) -> {
                    breathMode = which;
                    prefs.edit().putInt(Constants.KEY_BREATH_MODE, which).apply();
                })
                .setPositiveButton("继续", (dialog, which) -> showSafetyActionDialog(breathMode))
                .setNegativeButton(activity.getString(R.string.dialog_cancel), null)
                .show();
    }

    private void showSafetyActionDialog(int mode) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("选择下一步")
                .setMessage("你可以先开始呼吸练习。只有在你明确确认后，应用才会发送求助短信。")
                .setPositiveButton("开始呼吸练习", (dialog, which) ->
                        startBreathingIntervention(mode, false))
                .setNeutralButton("发送短信并开始", (dialog, which) ->
                        startBreathingIntervention(mode, true))
                .setNegativeButton(activity.getString(R.string.dialog_cancel), null)
                .show();
    }

    public void startBreathingIntervention(int mode) {
        startBreathingIntervention(mode, false);
    }

    private void startBreathingIntervention(int mode, boolean sendSms) {
        if (breathingEngine.isRunning()) return;
        host.triggerHaptic(activity.getWindow().getDecorView(), HapticFeedbackConstants.LONG_PRESS);
        if (SosActionPolicy.shouldSendEmergencySms(sendSms)) {
            sendEmergencySms();
        }
        layoutBreathing.setVisibility(View.VISIBLE);
        layoutBreathing.setAlpha(0f);
        layoutBreathing.animate().alpha(1f).setDuration(500).start();
        if (breathOverlay != null) {
            breathOverlay.setVisibility(View.VISIBLE);
            breathOverlay.startBreathing(Constants.BREATH_PHASES[mode][0]);
        }
        breathingEngine.start(mode);
    }

    public void stopBreathingIntervention() {
        hasSentSmsThisSession = false;
        breathingEngine.stop();
        if (breathOverlay != null) breathOverlay.stopBreathing();
        layoutBreathing.animate().cancel();
        layoutBreathing.animate().alpha(0f).setDuration(500)
                .withEndAction(() -> {
                    layoutBreathing.setVisibility(View.GONE);
                    layoutBreathing.setAlpha(1f);
                }).start();
        host.setSosVisible(false);
    }

    public void stop() {
        breathingEngine.stop();
        if (breathOverlay != null) breathOverlay.stopBreathing();
    }

    private void sendEmergencySms() {
        if (hasSentSmsThisSession) return;
        if (System.currentTimeMillis() - lastSmsTime < Constants.SOS_SMS_COOLDOWN_MS) {
            host.showMessage("SOS 短信刚刚已发送，请稍后再试");
            return;
        }
        String contact = host.emergencyContact();
        if (contact.trim().isEmpty()) {
            host.showMessage("请先在“我的”页设置紧急联系人");
            return;
        }
        if (!host.checkSmsPermission()) {
            host.requestSmsPermission();
            host.showMessage("需要短信权限后才能发送 SOS 求助短信");
            return;
        }
        try {
            SmsManager.getDefault().sendTextMessage(contact, null,
                    activity.getString(R.string.sos_sms_body), null, null);
            hasSentSmsThisSession = true;
            lastSmsTime = System.currentTimeMillis();
            host.showMessage("SOS 求助短信已发送");
        } catch (Exception e) {
            Log.e(Constants.TAG, "SOS SMS failed", e);
            host.showMessage("SOS 短信发送失败，请检查号码、SIM 卡或短信权限");
        }
    }
}
