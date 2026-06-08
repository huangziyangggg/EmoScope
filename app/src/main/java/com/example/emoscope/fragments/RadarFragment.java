package com.example.emoscope.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.emoscope.Constants;
import com.example.emoscope.EmoLineChartView;
import com.example.emoscope.R;
import com.example.emoscope.viewmodels.RadarViewModel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 首页 — V2.0 重构：语音优先、今日状态、情绪指数、趋势小图、AI 观察。
 * 面容分析和环境光感已从此页移除（降级到相机模式/工坊）。
 */
public class RadarFragment extends Fragment {

    public interface Callback {
        void onFaceCardClicked();
        void onSOSClicked();
        void onTtsToggled();
        void onVoiceButtonPressed();
        void onVoiceButtonReleased();
        void onQuickMoodClicked();
    }

    private Callback callback;
    private RadarViewModel vm;

    // ── 视图 ──
    private ImageView tvMoodEmoji, tvTtsIcon, ivGreetingIcon;
    private TextView tvDateTop, tvDynamicGreeting, tvStreak, tvMoodLabel;
    private TextView tvMoodScore, tvMoodChange, tvAiTyping, btnSpeakMain;
    private CardView cvSOS, cvStreak, cvAiResponse;
    private FrameLayout miniChartContainer;

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
        loadMoodScore();
        loadMiniChart();
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
        tvStreak = v.findViewById(R.id.tvStreak);
        cvStreak = v.findViewById(R.id.cvStreak);
        tvMoodEmoji = v.findViewById(R.id.tvMoodEmoji);
        tvMoodLabel = v.findViewById(R.id.tvMoodLabel);
        tvMoodScore = v.findViewById(R.id.tvMoodScore);
        tvMoodChange = v.findViewById(R.id.tvMoodChange);
        tvAiTyping = v.findViewById(R.id.tvAiTyping);
        btnSpeakMain = v.findViewById(R.id.btnSpeakMain);
        tvTtsIcon = v.findViewById(R.id.tvTtsIcon);
        cvSOS = v.findViewById(R.id.cvSOS);
        cvAiResponse = v.findViewById(R.id.cvAiResponse);
        miniChartContainer = v.findViewById(R.id.miniChartContainer);
    }

    // ═══════════════════════════ ViewModel ═══════════════════════════
    private void observeViewModel() {
        // 首页不再从面容分析获取情绪，改为从数据库计算

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
        v.findViewById(R.id.btnSOS).setOnClickListener(view -> {
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

        View btnContainer = v.findViewById(R.id.btnContainerMain);
        btnSpeakMain.setOnTouchListener((view, motionEvent) -> {
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

        if (getActivity() != null) {
            android.content.SharedPreferences prefs = requireActivity()
                    .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            int streak = prefs.getInt(Constants.KEY_STREAK_COUNT, 0);
            if (streak > 1) {
                tvStreak.setText(String.format(getString(R.string.streak_format), streak));
                cvStreak.setVisibility(View.VISIBLE);
            } else {
                cvStreak.setVisibility(View.GONE);
            }
        }
    }

    // ═══════════════════════════ 7日迷你图 ═══════════════════════════
    private void loadMiniChart() {
        if (getActivity() == null || miniChartContainer == null) return;
        miniChartContainer.removeAllViews();

        // 从 Activity 获取数据库
        com.example.emoscope.EmoDatabaseHelper dbHelper =
                ((com.example.emoscope.MainActivity) requireActivity()).getDbHelper();
        java.util.concurrent.ExecutorService executor =
                ((com.example.emoscope.MainActivity) requireActivity()).getBackgroundExecutor();

        executor.execute(() -> {
            android.database.sqlite.SQLiteDatabase db = dbHelper.getReadableDatabase();
            android.database.Cursor cursor = null;
            try {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, -7);
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
                String weekAgo = sdf.format(cal.getTime());

                cursor = db.rawQuery("SELECT " + Constants.COL_POSITIVE + " FROM "
                        + Constants.TABLE_RECORDS + " WHERE " + Constants.COL_TIME
                        + " >= ? ORDER BY " + Constants.COL_ID, new String[]{weekAgo});

                java.util.List<Float> data = new java.util.ArrayList<>();
                while (cursor.moveToNext()) {
                    data.add(cursor.getInt(0) == 1 ? 75f : 25f);
                }

                if (data.isEmpty()) {
                    for (int i = 0; i < 7; i++) data.add(50f);
                }

                final java.util.List<Float> chartData = data;
                requireActivity().runOnUiThread(() -> {
                    if (miniChartContainer != null) {
                        miniChartContainer.addView(new EmoLineChartView(
                                requireContext(), chartData, new java.util.ArrayList<>()));
                    }
                });
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        });
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

    /** 从数据库加载今日和昨日的情绪分 */
    public void loadMoodScore() {
        if (getActivity() == null) return;
        com.example.emoscope.EmoDatabaseHelper helper =
                ((com.example.emoscope.MainActivity) requireActivity()).getDbHelper();
        java.util.concurrent.ExecutorService exec =
                ((com.example.emoscope.MainActivity) requireActivity()).getBackgroundExecutor();

        exec.execute(() -> {
            android.database.sqlite.SQLiteDatabase db = helper.getReadableDatabase();
            android.database.Cursor cursor = null;
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
                String today = sdf.format(new Date());
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, -1);
                String yesterday = sdf.format(cal.getTime());

                // 今日数据
                cursor = db.rawQuery("SELECT " + Constants.COL_POSITIVE
                        + " FROM " + Constants.TABLE_RECORDS
                        + " WHERE " + Constants.COL_TIME + " LIKE '" + today + "%'", null);
                int todayTotal = cursor.getCount();
                int todayPos = 0;
                while (cursor.moveToNext()) {
                    if (cursor.getInt(0) == 1) todayPos++;
                }
                cursor.close();

                // 昨日数据
                cursor = db.rawQuery("SELECT " + Constants.COL_POSITIVE
                        + " FROM " + Constants.TABLE_RECORDS
                        + " WHERE " + Constants.COL_TIME + " LIKE '" + yesterday + "%'", null);
                int yesterdayTotal = cursor.getCount();
                int yesterdayPos = 0;
                while (cursor.moveToNext()) {
                    if (cursor.getInt(0) == 1) yesterdayPos++;
                }

                final float todayScore = todayTotal > 0 ? (float) todayPos / todayTotal * 100 : -1;
                final float yesterdayScore = yesterdayTotal > 0 ? (float) yesterdayPos / yesterdayTotal * 100 : -1;
                final int fTodayTotal = todayTotal;

                requireActivity().runOnUiThread(() -> {
                    if (tvMoodScore != null && todayScore >= 0) {
                        // 计数动画：从0跳到实际值
                        animateScore(tvMoodScore, 0, (int) todayScore);
                    }

                    // 根据分数更换图标
                    if (tvMoodEmoji != null) {
                        if (todayScore >= 70) {
                            tvMoodEmoji.setImageResource(R.drawable.ic_emotion_joy);
                            if (tvMoodLabel != null) tvMoodLabel.setText("开心");
                        } else if (todayScore >= 40) {
                            tvMoodEmoji.setImageResource(R.drawable.ic_emotion_calm);
                            if (tvMoodLabel != null) tvMoodLabel.setText("平静");
                        } else if (todayScore >= 0) {
                            tvMoodEmoji.setImageResource(R.drawable.ic_emotion_sad);
                            if (tvMoodLabel != null) tvMoodLabel.setText("低落");
                        }
                    }

                    // 昨日对比
                    if (tvMoodChange != null && todayScore >= 0 && yesterdayScore >= 0) {
                        float change = todayScore - yesterdayScore;
                        String arrow = change >= 0 ? "↑" : "↓";
                        int color = change >= 0 ? R.color.positive_green : R.color.danger_red;
                        tvMoodChange.setText(String.format(Locale.getDefault(),
                                "较昨日 %s %.0f%%", arrow, Math.abs(change)));
                        tvMoodChange.setTextColor(androidx.core.content.ContextCompat.getColor(
                                requireContext(), color));
                        tvMoodChange.setVisibility(View.VISIBLE);
                    }
                });
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        });
    }

    private void animateCardsEntrance(View root) {
        int[] cardIds = {
            R.id.cvStreak, R.id.cvAiResponse, R.id.miniChartContainer
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

    private void animateScore(TextView tv, int from, int to) {
        if (to <= 0) { tv.setText("--"); return; }
        final int duration = Math.min(800, Math.abs(to - from) * 20);
        final android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(from, to);
        anim.setDuration(duration);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addUpdateListener(a -> tv.setText(String.valueOf(a.getAnimatedValue())));
        anim.start();
    }

    public RadarViewModel getViewModel() { return vm; }
}
