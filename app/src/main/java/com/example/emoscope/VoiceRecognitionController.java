package com.example.emoscope;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.List;

/**
 * 语音识别控制器。
 *
 * 优先使用 Android 系统默认 SpeechRecognizer 服务，不绑定 Google 包名。
 * 在国产 Android 上，默认服务通常由小爱、小艺、Breeno、讯飞、百度输入法或系统语音输入提供。
 * 如果设备没有 RecognitionService，但提供系统语音识别对话框，则降级使用 RecognizerIntent。
 */
public class VoiceRecognitionController {

    public interface Callback {
        /** 最终识别结果 */
        void onFinalText(String text, VoiceFeatureAnalyzer.Result features);

        /** 没有检测到语音、用户取消或系统识别失败 */
        void onNoSpeech();

        /** 请求 Activity 启动系统语音对话框，作为没有 RecognitionService 时的兼容路径 */
        void onRequestSystemVoice(Intent intent, int requestCode);
    }

    public static final int REQUEST_CODE_SYSTEM_VOICE = 8172;

    private final Context context;
    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer speechRecognizer;
    private long recordingStartedAt;
    private boolean listening;
    private boolean usingIntentFallback;

    public VoiceRecognitionController(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    /**
     * 任意系统语音识别能力可用即可，不要求 Google。
     */
    public boolean isAvailable() {
        return isSpeechRecognizerAvailable() || isRecognizerIntentAvailable();
    }

    public boolean isListening() {
        return listening;
    }

    public void start() {
        if (listening) {
            return;
        }
        stop();

        listening = true;
        recordingStartedAt = System.currentTimeMillis();

        if (isSpeechRecognizerAvailable()) {
            startWithSystemSpeechRecognizer();
            return;
        }

        if (isRecognizerIntentAvailable()) {
            usingIntentFallback = true;
            callback.onRequestSystemVoice(createRecognizerIntent(), REQUEST_CODE_SYSTEM_VOICE);
            return;
        }

        listening = false;
        callback.onNoSpeech();
    }

    /**
     * 由 Activity.onActivityResult 调用，仅用于系统语音对话框 fallback。
     */
    public void handleActivityResult(int resultCode, Intent data) {
        if (!listening || !usingIntentFallback) {
            return;
        }
        listening = false;
        usingIntentFallback = false;

        if (resultCode != Activity.RESULT_OK || data == null) {
            callback.onNoSpeech();
            return;
        }

        deliverMatches(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
    }

    public void stop() {
        listening = false;
        usingIntentFallback = false;
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (RuntimeException ignored) {
                // 系统语音服务异常不应影响主流程。
            } finally {
                speechRecognizer = null;
            }
        }
    }

    private boolean isSpeechRecognizerAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    private boolean isRecognizerIntentAvailable() {
        PackageManager packageManager = context.getPackageManager();
        Intent recognizeIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        List<ResolveInfo> recognizeActivities = packageManager.queryIntentActivities(
                recognizeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (recognizeActivities != null && !recognizeActivities.isEmpty()) {
            return true;
        }

        Intent handsFreeIntent = new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
        List<ResolveInfo> handsFreeActivities = packageManager.queryIntentActivities(
                handsFreeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        return handsFreeActivities != null && !handsFreeActivities.isEmpty();
    }

    private void startWithSystemSpeechRecognizer() {
        mainHandler.post(() -> {
            if (!listening) {
                return;
            }
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) { }
                    @Override public void onBeginningOfSpeech() { }
                    @Override public void onRmsChanged(float rmsdB) { }
                    @Override public void onBufferReceived(byte[] buffer) { }
                    @Override public void onEndOfSpeech() { }
                    @Override public void onPartialResults(Bundle partialResults) { }
                    @Override public void onEvent(int eventType, Bundle params) { }

                    @Override
                    public void onError(int error) {
                        finishWithoutSpeech();
                    }

                    @Override
                    public void onResults(Bundle results) {
                        ArrayList<String> matches = results == null ? null
                                : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        deliverMatches(matches);
                    }
                });
                speechRecognizer.startListening(createRecognizerIntent());
            } catch (RuntimeException e) {
                listening = false;
                callback.onNoSpeech();
            }
        });
    }

    private Intent createRecognizerIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "说出此刻的感受…");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        return intent;
    }

    private void deliverMatches(ArrayList<String> matches) {
        listening = false;
        usingIntentFallback = false;
        destroyRecognizerAfterResult();

        if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0) == null ? "" : matches.get(0).trim();
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

    private void finishWithoutSpeech() {
        listening = false;
        usingIntentFallback = false;
        destroyRecognizerAfterResult();
        callback.onNoSpeech();
    }

    private void destroyRecognizerAfterResult() {
        if (speechRecognizer == null) {
            return;
        }
        try {
            speechRecognizer.destroy();
        } catch (RuntimeException ignored) {
            // ignore
        } finally {
            speechRecognizer = null;
        }
    }
}
