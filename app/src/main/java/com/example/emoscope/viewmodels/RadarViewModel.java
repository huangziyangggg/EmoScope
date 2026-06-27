package com.example.emoscope.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.emoscope.R;

/**
 * 情绪雷达页 ViewModel — 持有面容分析、光感、声纹、AI 响应等 UI 状态，
 * 配置变更（旋转屏幕）时自动保留数据。
 */
public class RadarViewModel extends ViewModel {

    // ── 面容分析 ──
    private final MutableLiveData<Integer> faceIcon = new MutableLiveData<>(R.drawable.ic_face_scan);
    private final MutableLiveData<String> emoProb1 = new MutableLiveData<>("扫描中...");
    private final MutableLiveData<String> emoProb2 = new MutableLiveData<>("");
    private final MutableLiveData<String> emoProb3 = new MutableLiveData<>("");
    private final MutableLiveData<Integer> moodIcon = new MutableLiveData<>(R.drawable.ic_emotion_calm);
    private final MutableLiveData<String> moodLabel = new MutableLiveData<>("实时情绪 · 检测中");

    // ── 光感 ──
    private final MutableLiveData<Integer> lightIcon = new MutableLiveData<>(R.drawable.ic_light_default);
    private final MutableLiveData<String> lightState = new MutableLiveData<>("感知中");

    // ── 声纹 ──
    private final MutableLiveData<String> voiceText = new MutableLiveData<>("长按按钮，开始语音记录...");
    private final MutableLiveData<String> voiceSpeed = new MutableLiveData<>("- 字/秒");
    private final MutableLiveData<String> voiceGentleHint = new MutableLiveData<>("");

    // ── AI 响应 ──
    private final MutableLiveData<String> aiResponse = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> aiCardVisible = new MutableLiveData<>(false);

    // ── SOS 按钮 ──
    private final MutableLiveData<Boolean> sosVisible = new MutableLiveData<>(false);

    // ── TTS 图标 ──
    private final MutableLiveData<Integer> ttsIcon = new MutableLiveData<>(R.drawable.ic_tts_on);

    // ── 语音录制状态 ──
    private final MutableLiveData<Boolean> isRecording = new MutableLiveData<>(false);
    private final MutableLiveData<String> voiceButtonText = new MutableLiveData<>("按住说话");

    // ═══════════════════════════════════════════════════════════════
    // 面容分析
    // ═══════════════════════════════════════════════════════════════
    public LiveData<Integer> getFaceIcon() { return faceIcon; }
    public LiveData<String> getEmoProb1() { return emoProb1; }
    public LiveData<String> getEmoProb2() { return emoProb2; }
    public LiveData<String> getEmoProb3() { return emoProb3; }
    public LiveData<Integer> getMoodIcon() { return moodIcon; }
    public LiveData<String> getMoodLabel() { return moodLabel; }

    public void setFaceResult(int icon, String prob1, String prob2, String prob3,
                               int moodIconRes, String moodLabelStr) {
        faceIcon.setValue(icon);
        emoProb1.setValue(prob1);
        emoProb2.setValue(prob2);
        emoProb3.setValue(prob3);
        moodIcon.setValue(moodIconRes);
        moodLabel.setValue(moodLabelStr);
    }

    public void setNoFace() {
        faceIcon.setValue(R.drawable.ic_face_scan);
        emoProb1.setValue("未检测到面部");
        emoProb2.setValue("--");
        emoProb3.setValue("--");
        moodIcon.setValue(R.drawable.ic_emotion_calm);
        moodLabel.setValue("实时情绪 · 检测中");
    }

    // ═══════════════════════════════════════════════════════════════
    // 光感
    // ═══════════════════════════════════════════════════════════════
    public LiveData<Integer> getLightIcon() { return lightIcon; }
    public LiveData<String> getLightState() { return lightState; }

    public void setLightState(int icon, String desc) {
        lightIcon.setValue(icon);
        lightState.setValue(desc);
    }

    // ═══════════════════════════════════════════════════════════════
    // 声纹
    // ═══════════════════════════════════════════════════════════════
    public LiveData<String> getVoiceText() { return voiceText; }
    public LiveData<String> getVoiceSpeed() { return voiceSpeed; }
    public LiveData<String> getVoiceGentleHint() { return voiceGentleHint; }

    public void setVoiceResult(String text, String speed) {
        voiceText.setValue(text);
        voiceSpeed.setValue(speed);
    }

    public void setVoiceGentleHint(String hint) {
        voiceGentleHint.setValue(hint);
    }

    public void setVoiceText(String text) {
        voiceText.setValue(text);
    }

    public void setVoiceListening() {
        voiceText.setValue("倾听中...");
    }

    public void setVoiceNotHeard() {
        voiceText.setValue("未检测到语音，请长按按钮重试");
    }

    // ═══════════════════════════════════════════════════════════════
    // AI 响应
    // ═══════════════════════════════════════════════════════════════
    public LiveData<String> getAiResponse() { return aiResponse; }
    public LiveData<Boolean> getAiCardVisible() { return aiCardVisible; }

    public void setAiStarted() {
        aiResponse.setValue("AI 模型连接中...");
        aiCardVisible.setValue(true);
    }

    public void setAiResponse(String text) {
        aiResponse.setValue(text);
    }

    public void setAiError(String msg) {
        aiResponse.setValue(msg);
        aiCardVisible.setValue(true);
    }

    // ═══════════════════════════════════════════════════════════════
    // SOS / TTS / 录制
    // ═══════════════════════════════════════════════════════════════
    public LiveData<Boolean> getSosVisible() { return sosVisible; }
    public void setSosVisible(boolean visible) { sosVisible.setValue(visible); }

    public LiveData<Integer> getTtsIcon() { return ttsIcon; }
    public void setTtsIcon(int iconRes) { ttsIcon.setValue(iconRes); }

    public LiveData<Boolean> getIsRecording() { return isRecording; }
    public LiveData<String> getVoiceButtonText() { return voiceButtonText; }

    public void setRecording(boolean recording) {
        isRecording.setValue(recording);
        voiceButtonText.setValue(recording ? "正在录音..." : "按住说话");
    }

    public void setVoiceButtonText(String text) {
        voiceButtonText.setValue(text);
    }
}
