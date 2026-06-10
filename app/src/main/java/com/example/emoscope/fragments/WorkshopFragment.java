package com.example.emoscope.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.emoscope.Constants;
import com.example.emoscope.EmoDatabaseHelper;
import com.example.emoscope.MainActivity;
import com.example.emoscope.R;
import com.example.emoscope.StreakManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 成长页 — AI 今日洞察、情绪日记、正念冥想、感恩清单、AI 周报、成就系统。
 * 每日金句已删除（留存价值低），新增 AI 驱动的个性化洞察。
 */
public class WorkshopFragment extends Fragment {

    private EmoDatabaseHelper dbHelper;
    private java.util.concurrent.ExecutorService executor;
    private static final SimpleDateFormat SDF_DAY = new SimpleDateFormat("MM-dd", Locale.getDefault());

    private TextView tvAiInsight, tvJournalPreview, tvGratitudeContent;
    private TextView tvBadgeSummary, tvReportHint;
    private TextView tvLevelBadge, tvLevelName, tvLevelProgress;
    private LinearLayout llBadges;
    private MaterialCardView btnRefreshInsight, btnWriteJournal, btnEditGratitude;
    private MaterialCardView btnGenerateReport;
    private MaterialCardView btnMeditate3, btnMeditate5, btnMeditate10;
    private MaterialCardView cvAiInsight, cvWeeklyReport, cvLevel;
    private com.example.emoscope.ConfettiView confettiView;
    private View meditationOverlay;
    private int lastBadgeCount = 0;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            MainActivity act = (MainActivity) context;
            this.dbHelper = act.getDbHelper();
            this.executor = act.getBackgroundExecutor();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_workshop, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        loadLevel();
        setupAiInsight();
        setupFaceAnalysis();
        setupJournal();
        setupMeditation(view);
        setupGratitude();
        setupWeeklyReport();
        setupBadges();
    }

    private void bindViews(View v) {
        tvAiInsight = v.findViewById(R.id.tvAiInsight);
        tvJournalPreview = v.findViewById(R.id.tvJournalPreview);
        tvGratitudeContent = v.findViewById(R.id.tvGratitudeContent);
        tvBadgeSummary = v.findViewById(R.id.tvBadgeSummary);
        tvReportHint = v.findViewById(R.id.tvReportHint);
        tvLevelBadge = v.findViewById(R.id.tvLevelBadge);
        tvLevelName = v.findViewById(R.id.tvLevelName);
        tvLevelProgress = v.findViewById(R.id.tvLevelProgress);
        llBadges = v.findViewById(R.id.llBadges);
        btnRefreshInsight = v.findViewById(R.id.btnRefreshInsight);
        btnWriteJournal = v.findViewById(R.id.btnWriteJournal);
        btnEditGratitude = v.findViewById(R.id.btnEditGratitude);
        btnGenerateReport = v.findViewById(R.id.btnGenerateReport);
        btnMeditate3 = v.findViewById(R.id.btnMeditate3);
        btnMeditate5 = v.findViewById(R.id.btnMeditate5);
        btnMeditate10 = v.findViewById(R.id.btnMeditate10);
        cvAiInsight = v.findViewById(R.id.cvAiInsight);
        cvWeeklyReport = v.findViewById(R.id.cvWeeklyReport);
        cvLevel = v.findViewById(R.id.cvLevel);
        confettiView = v.findViewById(R.id.confettiView);
    }

    // ═══════════════════════════ 面容分析 ═══════════════════════════
    private void setupFaceAnalysis() {
        View v = getView();
        if (v == null) return;
        v.findViewById(R.id.btnOpenFaceCamera).setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onFaceCardClicked();
            }
        });
    }

    // ═══════════════════════════ AI 今日洞察 ═══════════════════════════
    private void setupAiInsight() {
        btnRefreshInsight.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            loadAiInsight();
        });
        loadAiInsight();
    }

    private void loadAiInsight() {
        tvAiInsight.setText("正在分析你的情绪模式...");
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            try {
                com.example.emoscope.AiMemoryEngine.MemoryResult result =
                        com.example.emoscope.AiMemoryEngine.analyze(db);

                if (result.totalRecords < 3) {
                    requireActivity().runOnUiThread(() ->
                            tvAiInsight.setText("记录更多情绪（至少 3 条），AI 将为你生成长期观察报告。\n\n试试对麦克风说说今天发生了什么吧。"));
                    db.close();
                    return;
                }

                final String report = result.toString();
                requireActivity().runOnUiThread(() -> tvAiInsight.setText(report));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        tvAiInsight.setText("分析暂时不可用，请稍后再试"));
            } finally {
                db.close();
            }
        });
    }

    // ═══════════════════════════ 情绪日记 ═══════════════════════════
    private void setupJournal() {
        loadJournalPreview();
        btnWriteJournal.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showJournalDialog();
        });
    }

    private void loadJournalPreview() {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = null;
            try {
                String today;
                synchronized (SDF_DAY) { today = SDF_DAY.format(new Date()); }
                cursor = db.rawQuery("SELECT " + Constants.COL_DETAIL + " FROM " + Constants.TABLE_RECORDS
                        + " WHERE (" + Constants.COL_TIME + " LIKE ? OR "
                        + Constants.COL_TIME + " LIKE ?) AND "
                        + Constants.COL_TYPE + " = ? ORDER BY " + Constants.COL_ID + " DESC LIMIT 1",
                        new String[]{
                                today + "%",
                                EmoDatabaseHelper.legacyDayPrefix(new Date()) + "%",
                                "心灵日记"});
                final String preview;
                if (cursor.moveToFirst()) preview = cursor.getString(0);
                else preview = "今天还没写日记，点击开始记录...";
                requireActivity().runOnUiThread(() -> tvJournalPreview.setText(preview));
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        });
    }

    private void showJournalDialog() {
        final EditText input = new EditText(requireContext());
        input.setMinLines(8);
        input.setMaxLines(16);
        input.setVerticalScrollBarEnabled(true);
        input.setHint("写写今天的心情、想法或任何想表达的事情...\n\n提示：\n• 今天发生了什么？\n• 它让我感觉如何？\n• 我从中学到了什么？");
        input.setPadding(24, 24, 24, 24);
        input.setGravity(android.view.Gravity.TOP);
        input.setTextSize(14);

        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = null;
            try {
                String today;
                synchronized (SDF_DAY) { today = SDF_DAY.format(new Date()); }
                cursor = db.rawQuery("SELECT " + Constants.COL_DETAIL + " FROM " + Constants.TABLE_RECORDS
                        + " WHERE (" + Constants.COL_TIME + " LIKE ? OR "
                        + Constants.COL_TIME + " LIKE ?) AND "
                        + Constants.COL_TYPE + " = ? ORDER BY " + Constants.COL_ID + " DESC LIMIT 1",
                        new String[]{
                                today + "%",
                                EmoDatabaseHelper.legacyDayPrefix(new Date()) + "%",
                                "心灵日记"});
                final String existing;
                if (cursor.moveToFirst()) existing = cursor.getString(0);
                else existing = "";
                requireActivity().runOnUiThread(() -> {
                    if (!existing.isEmpty()) input.setText(existing);
                });
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        });

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("情绪日记")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String content = input.getText().toString().trim();
                    if (content.isEmpty()) { showSnackbar("日记内容不能为空"); return; }
                    saveJournal(content);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void saveJournal(String content) {
        executor.execute(() -> {
            dbHelper.saveRecord("心灵日记", content, true);
            requireActivity().runOnUiThread(() -> {
                showSnackbar("日记已保存");
                loadJournalPreview();
                loadBadges();
                loadLevel();
            });
        });
    }

    // ═══════════════════════════ 正念冥想 ═══════════════════════════
    private void setupMeditation(View root) {
        View.OnClickListener starter = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            int minutes = 3;
            if (v == btnMeditate5) minutes = 5;
            else if (v == btnMeditate10) minutes = 10;
            startMeditation(root, minutes);
        };
        btnMeditate3.setOnClickListener(starter);
        btnMeditate5.setOnClickListener(starter);
        btnMeditate10.setOnClickListener(starter);
    }

    private void startMeditation(View root, int minutes) {
        if (meditationOverlay != null && meditationOverlay.getVisibility() == View.VISIBLE) return;

        android.widget.FrameLayout overlay = new android.widget.FrameLayout(requireContext());
        overlay.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.overlay_dark_90));
        overlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setClickable(true);

        TextView guideText = new TextView(requireContext());
        guideText.setText("闭上眼睛，专注呼吸...\n\n吸气... 感受空气充满肺部...\n呼气... 让所有紧张随气息释放...");
        guideText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        guideText.setTextSize(18);
        guideText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        guideText.setPadding(40, 0, 40, 0);

        final TextView timerText = new TextView(requireContext());
        final int[] seconds = {minutes * 60};
        timerText.setText(String.format(Locale.getDefault(), "%d:%02d", seconds[0] / 60, seconds[0] % 60));
        timerText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        timerText.setTextSize(48);
        timerText.setTypeface(null, android.graphics.Typeface.BOLD);
        timerText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        TextView closeBtn = new TextView(requireContext());
        closeBtn.setText("结束冥想");
        closeBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.overlay_white_50));
        closeBtn.setTextSize(14);
        closeBtn.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        closeBtn.setPadding(20, 40, 20, 20);

        LinearLayout overlayLayout = new LinearLayout(requireContext());
        overlayLayout.setOrientation(LinearLayout.VERTICAL);
        overlayLayout.setGravity(android.view.Gravity.CENTER);
        overlayLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayLayout.addView(guideText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        overlayLayout.addView(timerText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        overlayLayout.addView(closeBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        overlay.addView(overlayLayout);

        ViewGroup rootView = (ViewGroup) requireActivity().findViewById(android.R.id.content);
        rootView.addView(overlay);
        meditationOverlay = overlay;

        final android.os.Handler handler = new android.os.Handler(requireActivity().getMainLooper());
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                seconds[0]--;
                if (seconds[0] <= 0) {
                    rootView.removeView(overlay);
                    meditationOverlay = null;
                    showSnackbar("冥想完成！感觉如何？");
                    saveMeditationLog(minutes);
                    loadBadges();
                    return;
                }
                timerText.setText(String.format(Locale.getDefault(), "%d:%02d", seconds[0] / 60, seconds[0] % 60));
                if (seconds[0] % 30 == 0) {
                    String[] guides = {
                        "放松肩膀，感受重力的牵引...",
                        "观察你的呼吸，不要试图改变它...",
                        "如果有杂念，轻轻地把注意力带回呼吸...",
                        "从头到脚扫描你的身体，释放每一个部位的紧张...",
                        "感受此刻的宁静，这是属于你的时间...",
                        "每一次呼气，让压力离开你的身体...",
                    };
                    guideText.setText(guides[seconds[0] / 30 % guides.length]);
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(tick, 1000);

        closeBtn.setOnClickListener(v -> {
            handler.removeCallbacks(tick);
            rootView.removeView(overlay);
            meditationOverlay = null;
            showSnackbar("冥想已中断，下次继续");
        });
    }

    private void saveMeditationLog(int minutes) {
        executor.execute(() -> {
            dbHelper.saveRecord("正念冥想", "完成 " + minutes + " 分钟正念冥想练习", true);
            requireActivity().runOnUiThread(() -> {
                loadBadges();
                loadLevel();
            });
        });
    }

    // ═══════════════════════════ 感恩清单 ═══════════════════════════
    private void setupGratitude() {
        loadGratitude();
        btnEditGratitude.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showGratitudeDialog();
        });
    }

    private void loadGratitude() {
        SharedPreferences prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String today;
        synchronized (SDF_DAY) { today = SDF_DAY.format(new Date()); }
        String saved = prefs.getString("gratitude_" + today, "");
        if (!saved.isEmpty()) {
            tvGratitudeContent.setText(saved);
        }
    }

    private void showGratitudeDialog() {
        final EditText input = new EditText(requireContext());
        input.setMinLines(6);
        input.setHint("写下今天值得感恩的 3 件事：\n\n1. \n2. \n3. \n\n可以是小事：一杯好喝的咖啡、朋友的一条消息、一个温暖的微笑...");
        input.setPadding(24, 24, 24, 24);
        input.setTextSize(14);

        SharedPreferences prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String today;
        synchronized (SDF_DAY) { today = SDF_DAY.format(new Date()); }
        String existing = prefs.getString("gratitude_" + today, "");
        if (!existing.isEmpty()) input.setText(existing);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("感恩清单")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String content = input.getText().toString().trim();
                    if (content.isEmpty()) return;
                    prefs.edit().putString("gratitude_" + today, content).apply();
                    tvGratitudeContent.setText(content);
                    showSnackbar("感恩清单已更新");
                    loadBadges();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ═══════════════════════════ AI 周报 ═══════════════════════════
    private void setupWeeklyReport() {
        btnGenerateReport.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            generateWeeklyReport();
        });
    }

    private void generateWeeklyReport() {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT " + Constants.COL_TIME + ", "
                        + Constants.COL_POSITIVE + ", " + Constants.COL_DETAIL
                        + " FROM " + Constants.TABLE_RECORDS
                        + " ORDER BY " + Constants.COL_ID + " DESC LIMIT 80", null);

                int total = 0;
                int pos = 0, neg = 0;
                List<String> details = new ArrayList<>();
                while (cursor.moveToNext()) {
                    if (!EmoDatabaseHelper.isWithinLastDays(cursor.getString(0), 7)) continue;
                    total++;
                    if (cursor.getInt(1) == 1) pos++; else neg++;
                    String d = cursor.getString(2);
                    if (d != null && details.size() < 20) details.add(d);
                }

                if (total < 3) {
                    requireActivity().runOnUiThread(() ->
                            showSnackbar("需要至少 3 条本周记录才能生成报告"));
                    return;
                }

                float avgScore = (float) pos / total * 100;
                StringBuilder report = new StringBuilder();
                report.append("本周情绪报告\n\n");
                report.append("记录总数：").append(total).append(" 条\n");
                report.append("情绪均值：").append(String.format("%.0f", avgScore)).append(" 分\n");
                report.append("积极占比：").append(String.format("%.0f", (float) pos / total * 100)).append("%\n\n");

                if (avgScore >= 65) report.append("整体状态良好，继续保持当前的生活节奏。\n");
                else if (avgScore >= 40) report.append("情绪有些波动，属于正常范围。建议增加户外活动。\n");
                else report.append("本周情绪偏低，请多给自己一些关爱和休息时间。\n");

                final String finalReport = report.toString();
                requireActivity().runOnUiThread(() -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("本周情绪报告")
                            .setMessage(finalReport)
                            .setPositiveButton("关闭", null)
                            .setNeutralButton("分享文字", (d, w) -> {
                                Intent share = new Intent(Intent.ACTION_SEND);
                                share.setType("text/plain");
                                share.putExtra(Intent.EXTRA_TEXT, finalReport);
                                startActivity(Intent.createChooser(share, "分享报告"));
                            })
                            .setNegativeButton("导出图片", (d, w) -> exportReportImage(finalReport))
                            .show();
                });
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        });
    }

    // ═══════════════════════════ 成就系统 ═══════════════════════════
    private void setupBadges() { loadBadges(); }

    private void loadBadges() {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT " + Constants.COL_TYPE + " FROM " + Constants.TABLE_RECORDS, null);
                int totalRecords = cursor.getCount();
                cursor.close(); cursor = null;

                int journalCount = 0, meditationCount = 0, manualCount = 0;
                cursor = db.rawQuery("SELECT " + Constants.COL_TYPE + " FROM " + Constants.TABLE_RECORDS, null);
                while (cursor.moveToNext()) {
                    String type = cursor.getString(0);
                    if (type == null) continue;
                    if (type.contains("日记")) journalCount++;
                    else if (type.contains("冥想")) meditationCount++;
                    else if (type.contains("手动")) manualCount++;
                }
                cursor.close();

                SharedPreferences prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
                int streak = prefs.getInt(Constants.KEY_STREAK_COUNT, 0);

                int gratitudeDays = 0;
                Calendar cal = Calendar.getInstance();
                for (int i = 0; i < 30; i++) {
                    String dateStr;
                    synchronized (SDF_DAY) { dateStr = SDF_DAY.format(cal.getTime()); }
                    if (!prefs.getString("gratitude_" + dateStr, "").isEmpty()) gratitudeDays++;
                    cal.add(Calendar.DAY_OF_MONTH, -1);
                }

                final List<String[]> badges = new ArrayList<>();
                String badgeIcon = String.valueOf(R.drawable.ic_workshop_badge);
                if (totalRecords >= 1) badges.add(new String[]{badgeIcon, "初来乍到", "第一次情绪记录"});
                if (totalRecords >= 10) badges.add(new String[]{badgeIcon, "数据收集者", "累计 10 条记录"});
                if (totalRecords >= 50) badges.add(new String[]{badgeIcon, "情绪达人", "累计 50 条记录"});
                if (streak >= 3) badges.add(new String[]{badgeIcon, "三日坚持", "连续 3 天记录"});
                if (streak >= 7) badges.add(new String[]{badgeIcon, "一周之星", "连续 7 天记录"});
                if (streak >= 30) badges.add(new String[]{badgeIcon, "月度王者", "连续 30 天记录"});
                if (journalCount >= 1) badges.add(new String[]{badgeIcon, "日记起步", "写了第一篇日记"});
                if (journalCount >= 5) badges.add(new String[]{badgeIcon, "日记爱好者", "写了 5 篇日记"});
                if (meditationCount >= 1) badges.add(new String[]{badgeIcon, "冥想初体验", "完成第一次冥想"});
                if (meditationCount >= 5) badges.add(new String[]{badgeIcon, "正念修行者", "完成 5 次冥想"});
                if (gratitudeDays >= 1) badges.add(new String[]{badgeIcon, "感恩之心", "写下第一份感恩清单"});
                if (gratitudeDays >= 7) badges.add(new String[]{badgeIcon, "感恩践行者", "7 天感恩记录"});

                int maxDisplay = Math.min(badges.size(), 6);
                final List<String[]> displayBadges = badges.subList(
                        Math.max(0, badges.size() - maxDisplay), badges.size());

                requireActivity().runOnUiThread(() -> {
                    llBadges.removeAllViews();
                    for (String[] badge : displayBadges) {
                        LinearLayout badgeView = new LinearLayout(requireContext());
                        badgeView.setOrientation(LinearLayout.VERTICAL);
                        badgeView.setGravity(android.view.Gravity.CENTER);
                        badgeView.setPadding(8, 4, 8, 4);
                        badgeView.setLayoutParams(new LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                        MaterialCardView circle = new MaterialCardView(requireContext());
                        circle.setCardBackgroundColor(ContextCompat.getColor(requireContext(),
                                R.color.cat_badge_container));
                        circle.setRadius(20 * getResources().getDisplayMetrics().density);
                        circle.setContentPadding(5, 5, 5, 5);
                        circle.setStrokeWidth(1);
                        circle.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.cat_badge));

                        ImageView icon = new ImageView(requireContext());
                        icon.setImageResource(Integer.parseInt(badge[0]));
                        icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.cat_badge));
                        circle.addView(icon);
                        badgeView.addView(circle);

                        TextView name = new TextView(requireContext());
                        name.setText(badge[1]);
                        name.setTextSize(10);
                        name.setTextColor(MaterialColors.getColor(requireContext(),
                                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                        name.setGravity(android.view.Gravity.CENTER);
                        name.setPadding(0, 4, 0, 0);
                        badgeView.addView(name);
                        llBadges.addView(badgeView);
                    }

                    if (displayBadges.isEmpty()) {
                        TextView hint = new TextView(requireContext());
                        hint.setText("开始记录情绪，解锁你的第一个徽章");
                        hint.setTextSize(12);
                        hint.setTextColor(MaterialColors.getColor(requireContext(),
                                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                        hint.setGravity(android.view.Gravity.CENTER);
                        llBadges.addView(hint, new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    }

                    if (tvBadgeSummary != null) {
                        tvBadgeSummary.setText(String.format(Locale.getDefault(), "%d/12", badges.size()));
                    }

                    // 新徽章解锁 → 彩带！
                    if (badges.size() > lastBadgeCount && lastBadgeCount > 0 && confettiView != null) {
                        confettiView.burst();
                    }
                    lastBadgeCount = badges.size();
                });
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        });
    }

    // ═══════════════════════════ 成长等级 ═══════════════════════════
    private void loadLevel() {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT COUNT(*) FROM " + Constants.TABLE_RECORDS, null);
                int totalRecords = 0;
                if (cursor.moveToFirst()) totalRecords = cursor.getInt(0);

                int level = 0;
                for (int i = Constants.LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
                    if (totalRecords >= Constants.LEVEL_THRESHOLDS[i]) {
                        level = i;
                        break;
                    }
                }
                int nextThreshold = level < Constants.LEVEL_THRESHOLDS.length - 1
                        ? Constants.LEVEL_THRESHOLDS[level + 1] : totalRecords;
                int need = Math.max(0, nextThreshold - totalRecords);

                final int fLevel = level;
                final int fTotal = totalRecords;
                final int fNeed = need;

                requireActivity().runOnUiThread(() -> {
                    if (tvLevelBadge != null)
                        tvLevelBadge.setText("Lv" + (fLevel + 1));
                    if (tvLevelName != null)
                        tvLevelName.setText(Constants.LEVEL_NAMES[fLevel]);
                    if (tvLevelProgress != null) {
                        if (fNeed > 0) {
                            tvLevelProgress.setText(fTotal + "/" + nextThreshold
                                    + " 条记录 · 升级还需 " + fNeed + " 条");
                        } else {
                            tvLevelProgress.setText(fTotal + " 条记录 · 已达最高等级");
                        }
                    }
                });
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        });
    }

    private void exportReportImage(String reportText) {
        float density = getResources().getDisplayMetrics().density;
        int width = (int) (320 * density);
        int padding = (int) (20 * density);
        int lineHeight = (int) (22 * density);

        // 计算高度
        String[] lines = reportText.split("\n");
        int height = padding * 2 + lines.length * lineHeight + (int) (60 * density);

        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

        // 背景
        android.graphics.Paint bgPaint = new android.graphics.Paint();
        bgPaint.setColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_surface));
        canvas.drawRoundRect(0, 0, width, height, 24 * density, 24 * density, bgPaint);

        // 顶部色条
        android.graphics.Paint stripePaint = new android.graphics.Paint();
        stripePaint.setColor(ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary));
        canvas.drawRoundRect(0, 0, width, 4 * density, 4 * density, 4 * density, stripePaint);

        // 标题
        android.graphics.Paint titlePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFF18181B);
        titlePaint.setTextSize(22 * density);
        titlePaint.setFakeBoldText(true);
        canvas.drawText("EmoScope 情绪周报", padding, padding + 24 * density, titlePaint);

        // 正文
        android.graphics.Paint textPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF52525B);
        textPaint.setTextSize(13 * density);
        float y = padding + 60 * density;
        for (String line : lines) {
            canvas.drawText(line, padding, y, textPaint);
            y += lineHeight;
        }

        // 保存
        java.io.File dir = new java.io.File(requireContext().getExternalFilesDir(null), "Reports");
        if (!dir.exists()) dir.mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
        java.io.File file = new java.io.File(dir, "EmoScope_Weekly_" + ts + ".png");
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/png");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享周报图片"));
            showSnackbar("周报图片已生成");
        } catch (Exception e) {
            showSnackbar("导出失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════ 公开方法 ═══════════════════════════
    public void refreshUI() {
        if (tvAiInsight == null) return;
        loadLevel();
        loadAiInsight();
        loadJournalPreview();
        loadGratitude();
        loadBadges();
    }

    private void showSnackbar(String msg) {
        View v = getView();
        if (v != null) Snackbar.make(v, msg, Snackbar.LENGTH_SHORT).show();
    }
}
