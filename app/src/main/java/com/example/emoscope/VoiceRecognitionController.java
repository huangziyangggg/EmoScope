package com.example.emoscope;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * Owns Android SpeechRecognizer lifecycle and reports plain voice events to UI code.
 */
public class VoiceRecognitionController {

    public interface Callback {
        void onReady();
        void onProcessing();
        void onPartialText(String text);
        void onFinalText(String text);
        void onNoSpeech();
        void onError(String message);
    }

    private final Context context;
    private final Callback callback;
    private SpeechRecognizer recognizer;

    public VoiceRecognitionController(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public boolean isAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public void start() {
        stop();

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        } catch (Exception e) {
            callback.onError("语音识别服务不可用，请安装 Google 语音搜索");
            return;
        }

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                callback.onReady();
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}

            @Override public void onEndOfSpeech() {
                callback.onProcessing();
            }

            @Override public void onError(int error) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    callback.onNoSpeech();
                    return;
                }
                callback.onError(errorMessage(error));
            }

            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    callback.onFinalText(matches.get(0).trim());
                } else {
                    callback.onNoSpeech();
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {
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

    private String errorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "麦克风录音失败，请检查设备麦克风";
            case SpeechRecognizer.ERROR_CLIENT:
                return "语音识别暂时不可用，请稍后重试";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "缺少麦克风权限，请在系统设置中允许录音";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "语音识别需要网络，请检查连接后重试";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "语音识别正在处理中，请稍后再试";
            case SpeechRecognizer.ERROR_SERVER:
                return "系统语音服务暂时不可用，请稍后重试";
            default:
                return "语音识别失败，请稍后重试";
        }
    }

    public void stop() {
        if (recognizer == null) return;
        try { recognizer.stopListening(); } catch (Exception ignored) {}
        try { recognizer.destroy(); } catch (Exception ignored) {}
        recognizer = null;
    }
}
