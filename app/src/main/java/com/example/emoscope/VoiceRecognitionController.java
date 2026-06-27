package com.example.emoscope;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.List;

/**
 * 语音识别控制器 —— 双路径策略。
 *
 * 路径 A（优先）：SpeechRecognizer 嵌入式识别
 *   需要 Google Speech Services，适合有 GMS 的国际设备。
 *   优点：可内嵌 UI、支持实时语音波形。
 *
 * 路径 B（回退）：RecognizerIntent 系统语音对话框
 *   几乎所有 Android 设备都支持（包括国产手机），由系统
 *   内置语音服务（小爱/小艺/Breeno/百度输入法等）接管。
 *   优点：不依赖 Google 服务，在国产手机上最可靠。
 */
public class VoiceRecognitionController {

    public interface Callback {
        /** 嵌入式识别就绪 */
        void onReady();
        /** 正在处理 */
        void onProcessing();
        /** 实时部分文字（仅路径 A） */
        void onPartialText(String text);
        /** 最终识别结果 */
        void onFinalText(String text, VoiceFeatureAnalyzer.Result features);
        /** 没有检测到语音 */
        void onNoSpeech();
        /** 错误 */
        void onError(String message);
        /** 请求 Activity 启动系统语音对话框（路径 B 回退） */
        void onRequestSystemVoice(Intent intent, int requestCode);
    }

    public static final int REQUEST_CODE_SYSTEM_VOICE = 8172;

    private final Context context;
    private final Callback callback;
    private SpeechRecognizer recognizer;
    private long recordingStartedAt;
    private float rmsTotal;
    private float rmsPeak;
    private int rmsSamples;
    private boolean listening;
    private boolean usingSystemFallback;

    public VoiceRecognitionController(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    /**
     * 检查是否有任何一种语音识别方式可用。
     * 优先检查 Google SpeechRecognizer，不可用时检查系统 Intent 是否有响应方。
     */
    public boolean isAvailable() {
        // 路径 A：Google SpeechRecognizer
        if (SpeechRecognizer.isRecognitionAvailable(context)) return true;

        // 路径 B：系统语音对话框 —— 检查是否有 Activity 能响应
        Intent probe = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        probe.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        List<ResolveInfo> activities = context.getPackageManager()
                .queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY);
        return !activities.isEmpty();
    }

    /** 当前是否正在识别 */
    public boolean isListening() { return listening; }

    /**
     * 开始语音识别。优先走嵌入式路径 A，不可用时走系统对话框路径 B。
     */
    public void start() {
        if (listening) return;
        stop();

        // 尝试路径 A：Google SpeechRecognizer
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            startEmbedded();
            return;
        }

        // 回退到路径 B：系统语音对话框
        startSystemDialog();
    }

    // ── 路径 A：嵌入式识别 ──────────────────────────────────────

    private void startEmbedded() {
        listening = true;
        usingSystemFallback = false;
        recordingStartedAt = System.currentTimeMillis();
        rmsTotal = 0f;
        rmsPeak = 0f;
        rmsSamples = 0;

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        } catch (Exception e) {
            // 创建失败 → 回退到系统对话框
            listening = false;
            startSystemDialog();
            return;
        }
        if (recognizer == null) {
            listening = false;
            startSystemDialog();
            return;
        }

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                if (listening) callback.onReady();
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {
                if (rmsdB <= 0f) return;
                rmsTotal += rmsdB;
                rmsPeak = Math.max(rmsPeak, rmsdB);
                rmsSamples++;
            }
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                if (listening) callback.onProcessing();
            }
            @Override public void onError(int error) {
                if (!listening) return;
                listening = false;
                if (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    callback.onNoSpeech();
                    return;
                }
                // 嵌入识别失败 → 尝试回退到系统对话框
                if (error == SpeechRecognizer.ERROR_CLIENT
                        || error == SpeechRecognizer.ERROR_SERVER
                        || error == SpeechRecognizer.ERROR_NETWORK
                        || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                    startSystemDialog();
                    return;
                }
                callback.onError(errorMessage(error));
            }
            @Override public void onResults(Bundle results) {
                if (!listening) return;
                listening = false;
                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0).trim();
                    callback.onFinalText(text, buildVoiceFeatures(text));
                } else {
                    callback.onNoSpeech();
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {
                if (!listening) return;
                ArrayList<String> matches = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    callback.onPartialText(matches.get(0).trim());
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizer.startListening(intent);
    }

    // ── 路径 B：系统语音对话框 ──────────────────────────────────

    private void startSystemDialog() {
        listening = true;
        usingSystemFallback = true;
        recordingStartedAt = System.currentTimeMillis();
        rmsTotal = 0f; rmsPeak = 0f; rmsSamples = 0;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "说出此刻的感受…");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        callback.onRequestSystemVoice(intent, REQUEST_CODE_SYSTEM_VOICE);
    }

    /**
     * 由 Activity.onActivityResult 调用，处理系统语音对话框的返回结果。
     */
    public void handleActivityResult(int resultCode, Intent data) {
        if (!listening || !usingSystemFallback) return;
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
                callback.onFinalText(text, buildVoiceFeatures(text));
                return;
            }
        }
        callback.onNoSpeech();
    }

    // ── 停用 ────────────────────────────────────────────────────

    public void stop() {
        listening = false;
        usingSystemFallback = false;
        if (recognizer == null) return;
        try { recognizer.stopListening(); } catch (Exception ignored) {}
        try { recognizer.destroy(); } catch (Exception ignored) {}
        recognizer = null;
    }

    // ── 辅助 ────────────────────────────────────────────────────

    private VoiceFeatureAnalyzer.Result buildVoiceFeatures(String text) {
        float durationSec = Math.max(0.5f,
                (System.currentTimeMillis() - recordingStartedAt) / 1000f);
        float averageRms = rmsSamples == 0 ? 0f : rmsTotal / rmsSamples;
        return VoiceFeatureAnalyzer.analyze(text, durationSec, averageRms, rmsPeak, rmsSamples);
    }

    private String errorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "麦克风未就绪，请检查设备。";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "缺少麦克风权限，请在系统设置中允许。";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "语音识别正在处理中，请稍后再试。";
            default:
                return "识别未完成，可再试一次。";
        }
    }
}
