package com.example.emoscope;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.RecognizerIntent;

import java.util.ArrayList;
import java.util.List;

/**
 * 语音识别控制器 —— 纯系统语音对话框方案。
 *
 * 使用 RecognizerIntent.ACTION_RECOGNIZE_SPEECH 启动系统语音对话框，
 * 由系统内置语音服务（小爱/小艺/Breeno/百度输入法等）接管语音识别。
 * 不依赖 Google SpeechRecognizer，适配所有国产手机。
 */
public class VoiceRecognitionController {

    public interface Callback {
        /** 最终识别结果 */
        void onFinalText(String text, VoiceFeatureAnalyzer.Result features);
        /** 没有检测到语音或用户取消 */
        void onNoSpeech();
        /** 请求 Activity 启动系统语音对话框 */
        void onRequestSystemVoice(Intent intent, int requestCode);
    }

    public static final int REQUEST_CODE_SYSTEM_VOICE = 8172;

    private final Context context;
    private final Callback callback;
    private long recordingStartedAt;
    private boolean listening;

    public VoiceRecognitionController(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    /**
     * 检查系统是否有语音识别服务可用（任一国产语音服务响应即可）
     */
    public boolean isAvailable() {
        Intent probe = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        List<ResolveInfo> activities = context.getPackageManager()
                .queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY);
        return activities != null && !activities.isEmpty();
    }

    public boolean isListening() { return listening; }

    /**
     * 启动系统语音对话框
     */
    public void start() {
        if (listening) return;
        stop();

        listening = true;
        recordingStartedAt = System.currentTimeMillis();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "说出此刻的感受…");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        callback.onRequestSystemVoice(intent, REQUEST_CODE_SYSTEM_VOICE);
    }

    /**
     * 由 Activity.onActivityResult 调用
     */
    public void handleActivityResult(int resultCode, Intent data) {
        if (!listening) return;
        listening = false;

        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            callback.onNoSpeech();
            return;
        }

        ArrayList<String> matches = data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS);
        if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0).trim();
            if (!text.isEmpty()) {
                float durationSec = Math.max(0.5f,
                        (System.currentTimeMillis() - recordingStartedAt) / 1000f);
                VoiceFeatureAnalyzer.Result features =
                        VoiceFeatureAnalyzer.analyze(text, durationSec, 0f, 0f, 0);
                callback.onFinalText(text, features);
                return;
            }
        }
        callback.onNoSpeech();
    }

    public void stop() {
        listening = false;
    }
}
