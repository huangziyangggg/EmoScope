package com.example.emoscope.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.emoscope.R;
import com.example.emoscope.viewmodels.RadarViewModel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 首页 — 只保留快速记录、语音倾诉、面容分析三个核心入口。
 */
public class RadarFragment extends Fragment {

    public interface Callback {
        void onFaceCardClicked();
        void onSOSClicked();
        void onTtsToggled();
        void onVoiceButtonPressed();
        void onVoiceButtonReleased();
        void onQuickMoodClicked();
        void onDailyCareSecondaryClicked();
    }

    private Callback callback;
    private RadarViewModel vm;

    // ── 视图 ──
    private ImageView tvTtsIcon, ivGreetingIcon;
    private TextView tvDateTop, tvDynamicGreeting, tvAiTyping, btnSpeakMain;
    private CardView cvSOS, cvAiResponse;

    private static final SimpleDateFormat SDF_DATE_TOP = new SimpleDateFormat("M月d日 EEEE", Locale.CHINESE);

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Callback) callback = (Callback) context;
        else throw new ClassCastException(context + " must implement RadarFragment.Callback");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_radar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(requireActivity()).get(RadarViewModel.class);
        bindViews(view);
        observeViewModel();
        setupClickListeners(view);
        setDynamicGreetingAndDate();
        animateCardsEntrance(view);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callback = null;
    }

    // ═══════════════════════════ bindViews ═══════════════════════════
    private void bindViews(View v) {
        tvDateTop = v.findViewById(R.id.tvDateTop);
        tvDynamicGreeting = v.findViewById(R.id.tvDynamicGreeting);
        ivGreetingIcon = v.findViewById(R.id.ivGreetingIcon);
        tvAiTyping = v.findViewById(R.id.tvAiTyping);
        btnSpeakMain = v.findViewById(R.id.btnSpeakMain);
        tvTtsIcon = v.findViewById(R.id.tvTtsIcon);
        cvSOS = v.findViewById(R.id.cvSOS);
        cvAiResponse = v.findViewById(R.id.cvAiResponse);
    }

    // ═══════════════════════════ ViewModel ═══════════════════════════
    private void observeViewModel() {
        vm.getAiResponse().observe(getViewLifecycleOwner(), text -> {
            if (text != null && !text.isEmpty()) {
                tvAiTyping.setText(text);
            }
        });
        vm.getAiCardVisible().observe(getViewLifecycleOwner(), visible -> {
            cvAiResponse.setVisibility(Boolean.TRUE.equals(visible) ? View.VISIBLE : View.GONE);
        });

        vm.getSosVisible().observe(getViewLifecycleOwner(), visible -> {
            cvSOS.setVisibility(Boolean.TRUE.equals(visible) ? View.VISIBLE : View.GONE);
        });

        vm.getTtsIcon().observe(getViewLifecycleOwner(), tvTtsIcon::setImageResource);

        vm.getVoiceButtonText().observe(getViewLifecycleOwner(), text -> {
            if (text != null) btnSpeakMain.setText(text);
        });
    }

    // ═══════════════════════════ 点击事件 ═══════════════════════════
    private void setupClickListeners(View v) {
        // SOS 点击绑定到整个卡片区域（含图标和文字）
        v.findViewById(R.id.cvSOS).setOnClickListener(view -> {
            if (callback != null) callback.onSOSClicked();
        });

        v.findViewById(R.id.btnTtsToggle).setOnClickListener(view -> {
            if (callback != null) callback.onTtsToggled();
        });

        // 面容分析大卡片
        v.findViewById(R.id.cvFaceAnalysis).setOnClickListener(view -> {
            if (callback != null) callback.onFaceCardClicked();
        });
        v.findViewById(R.id.btnOpenFaceCamera).setOnClickListener(view -> {
            if (callback != null) callback.onFaceCardClicked();
        });

        v.findViewById(R.id.btnQuickMood).setOnClickListener(view -> {
            if (callback != null) callback.onQuickMoodClicked();
        });

        tvAiTyping.setOnLongClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("EmoScope",
                    tvAiTyping.getText().toString()));
            return true;
        });

        // 语音按钮 — 触摸监听绑定到整个卡片，扩大可点击区域
        View btnContainer = v.findViewById(R.id.btnContainerMain);
        btnContainer.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                if (callback != null) callback.onVoiceButtonPressed();
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP
                    || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                if (callback != null) callback.onVoiceButtonReleased();
            }
            return true;
        });
    }

    // ═══════════════════════════ 问候 + 打卡 ═════════════════════════
    private void setDynamicGreetingAndDate() {
        tvDateTop.setText(SDF_DATE_TOP.format(new Date()));
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        int iconRes;
        if (hour >= 0 && hour < 6) { greeting = getString(R.string.greeting_night); iconRes = R.drawable.ic_time_night; }
        else if (hour >= 6 && hour < 11) { greeting = getString(R.string.greeting_morning); iconRes = R.drawable.ic_time_morning; }
        else if (hour >= 11 && hour < 14) { greeting = getString(R.string.greeting_noon); iconRes = R.drawable.ic_time_noon; }
        else if (hour >= 14 && hour < 18) { greeting = getString(R.string.greeting_afternoon); iconRes = R.drawable.ic_time_afternoon; }
        else if (hour >= 18 && hour < 24) { greeting = getString(R.string.greeting_evening); iconRes = R.drawable.ic_time_evening; }
        else { greeting = getString(R.string.greeting_default); iconRes = R.drawable.ic_time_afternoon; }
        tvDynamicGreeting.setText(greeting);
        if (ivGreetingIcon != null) ivGreetingIcon.setImageResource(iconRes);
    }

    // ═══════════════════════════ 公开方法 ═══════════════════════════
    public void showTypewriterEffect(String text) {
        if (tvAiTyping == null) return;
        tvAiTyping.setText("");
        final int[] index = {0};
        final long delay = 20;
        final android.os.Handler handler = new android.os.Handler(requireActivity().getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (index[0] < text.length() && tvAiTyping != null) {
                    tvAiTyping.append(String.valueOf(text.charAt(index[0])));
                    index[0]++;
                    handler.postDelayed(this, delay);
                }
            }
        });
    }

    private void animateCardsEntrance(View root) {
        int[] cardIds = {
            R.id.btnQuickMood, R.id.btnContainerMain, R.id.cvFaceAnalysis
        };
        for (int i = 0; i < cardIds.length; i++) {
            View card = root.findViewById(cardIds[i]);
            if (card != null && card.getVisibility() == View.VISIBLE) {
                card.setTranslationY(30f);
                card.setAlpha(0f);
                card.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(350)
                    .setStartDelay(100L * i)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            }
        }
    }

    public RadarViewModel getViewModel() { return vm; }

    public void refreshDailyLoop() {
        setDynamicGreetingAndDate();
    }
}
