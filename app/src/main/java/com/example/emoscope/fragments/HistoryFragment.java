package com.example.emoscope.fragments;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.emoscope.AiMemoryEngine;
import com.example.emoscope.AppBrand;
import com.example.emoscope.Constants;
import com.example.emoscope.EmoDatabaseHelper;
import com.example.emoscope.StreakManager;
import com.example.emoscope.EmoLineChartView;
import com.example.emoscope.HistoryAdapter;
import com.example.emoscope.MainActivity;
import com.example.emoscope.MoodDialogHelper;
import com.example.emoscope.MoodSelectionPolicy;
import com.example.emoscope.R;
import com.example.emoscope.ResearchDataExporter;
import com.example.emoscope.history.HistoryBackupFormatter;
import com.example.emoscope.history.HistoryEmptyStatePolicy;
import com.example.emoscope.history.HistoryExportFormatter;
import com.example.emoscope.history.HistoryExportRepository;
import com.example.emoscope.viewmodels.HistoryViewModel;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆流页 — 情绪趋势图表、历史记录列表、筛选与导出。
 * 数据通过 HistoryViewModel 管理，数据库操作在后台线程执行。
 */
public class HistoryFragment extends Fragment {

    private HistoryViewModel vm;
    private EmoDatabaseHelper dbHelper;
    private java.util.concurrent.ExecutorService executor;

    // ── 视图 ──
    private SwipeRefreshLayout swipeHistory;
    private RecyclerView rvHistory;
    private HistoryAdapter historyAdapter;
    private TextView tvHistoryEmpty, tvChartEmpty, tvStatTotal, tvStatPos, tvStatNeg, tvStatAvg;
    private FrameLayout chartContainer;
    private View filterAll, filterWeek, filterMonth, btnHistoryEmptyRecord;
    private ImageView filterPos, filterWarn;
    private TextView tvNotifyTime; // 可能为 null（在 settings 中）

    private static final SimpleDateFormat SDF_DAY = new SimpleDateFormat("MM-dd", Locale.getDefault());

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // 从 Activity 获取共享资源
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
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(requireActivity()).get(HistoryViewModel.class);

        bindViews(view);
        setupRecyclerView();
        setupFilters(view);
        setupMoreMenu(view);
        setupSwipeRefresh();
        observeViewModel();
        loadHistoryData();
    }

    private void bindViews(View v) {
        swipeHistory = v.findViewById(R.id.swipeHistory);
        rvHistory = v.findViewById(R.id.rvHistory);
        tvHistoryEmpty = v.findViewById(R.id.tvHistoryEmpty);
        btnHistoryEmptyRecord = v.findViewById(R.id.btnHistoryEmptyRecord);
        btnHistoryEmptyRecord.setOnClickListener(view -> showManualMoodDialog());
        tvChartEmpty = v.findViewById(R.id.tvChartEmpty);
        tvStatTotal = v.findViewById(R.id.tvStatTotal);
        tvStatPos = v.findViewById(R.id.tvStatPos);
        tvStatNeg = v.findViewById(R.id.tvStatNeg);
        tvStatAvg = v.findViewById(R.id.tvStatAvg);
        chartContainer = v.findViewById(R.id.chartContainer);
        filterAll = v.findViewById(R.id.filterAll);
        filterWeek = v.findViewById(R.id.filterWeek);
        filterMonth = v.findViewById(R.id.filterMonth);
        filterPos = v.findViewById(R.id.filterPositive);
        filterWarn = v.findViewById(R.id.filterWarning);
        filterPos.setColorFilter(MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        filterWarn.setColorFilter(MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
    }

    private void setupRecyclerView() {
        rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        rvHistory.setHasFixedSize(true);
        historyAdapter = new HistoryAdapter();
        rvHistory.setAdapter(historyAdapter);
    }

    private void observeViewModel() {
        vm.getHistoryItems().observe(getViewLifecycleOwner(), items -> historyAdapter.setItems(items));
        vm.getStatTotal().observe(getViewLifecycleOwner(), c -> tvStatTotal.setText(String.valueOf(c)));
        vm.getStatPos().observe(getViewLifecycleOwner(), c -> tvStatPos.setText(String.valueOf(c)));
        vm.getStatNeg().observe(getViewLifecycleOwner(), c -> tvStatNeg.setText(String.valueOf(c)));
        vm.getStatAvg().observe(getViewLifecycleOwner(), avg -> {
            if (avg >= 0) tvStatAvg.setText(String.format(Locale.getDefault(), "%.0f", avg));
            else tvStatAvg.setText("--");
        });
        vm.getIsEmpty().observe(getViewLifecycleOwner(), empty -> {
            boolean isEmpty = Boolean.TRUE.equals(empty);
            tvHistoryEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            btnHistoryEmptyRecord.setVisibility(HistoryEmptyStatePolicy.shouldShowRecordAction(isEmpty)
                    ? View.VISIBLE : View.GONE);
            rvHistory.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });
        vm.getIsRefreshing().observe(getViewLifecycleOwner(), refreshing -> {
            if (swipeHistory != null) swipeHistory.setRefreshing(Boolean.TRUE.equals(refreshing));
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 筛选
    // ═══════════════════════════════════════════════════════════════
    private void setupFilters(View v) {
        filterAll.setOnClickListener(view -> {
            vm.setDateFilter(0);
            updateFilterUI(filterAll, filterWeek, filterMonth);
            loadHistoryData();
        });
        filterWeek.setOnClickListener(view -> {
            vm.setDateFilter(2);
            updateFilterUI(filterWeek, filterAll, filterMonth);
            loadHistoryData();
        });
        filterMonth.setOnClickListener(view -> {
            vm.setDateFilter(3);
            updateFilterUI(filterMonth, filterAll, filterWeek);
            loadHistoryData();
        });
        filterPos.setOnClickListener(view -> {
            int current = vm.getMoodFilter().getValue() != null ? vm.getMoodFilter().getValue() : 0;
            int next = (current == 1) ? 0 : 1;
            vm.setMoodFilter(next);
            filterPos.setColorFilter(next == 1
                    ? requireContext().getColor(R.color.positive_green)
                    : MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
            filterWarn.setColorFilter(MaterialColors.getColor(requireContext(),
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
            loadHistoryData();
        });
        filterWarn.setOnClickListener(view -> {
            int current = vm.getMoodFilter().getValue() != null ? vm.getMoodFilter().getValue() : 0;
            int next = (current == 2) ? 0 : 2;
            vm.setMoodFilter(next);
            filterWarn.setColorFilter(next == 2
                    ? MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorError, 0)
                    : MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
            filterPos.setColorFilter(MaterialColors.getColor(requireContext(),
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
            loadHistoryData();
        });
    }

    private void updateFilterUI(View selected, View... others) {
        selected.setBackgroundResource(R.drawable.bg_history_filter_selected);
        ((TextView) selected).setTextColor(MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorPrimary, 0));
        for (View v : others) {
            v.setBackgroundColor(0x00000000);
            ((TextView) v).setTextColor(MaterialColors.getColor(requireContext(),
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        }
    }

    private void setupSwipeRefresh() {
        swipeHistory.setOnRefreshListener(() -> {
            vm.setRefreshing(true);
            loadHistoryData();
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 更多菜单
    // ═══════════════════════════════════════════════════════════════
    private void setupMoreMenu(View v) {
        v.findViewById(R.id.btnHistoryMore).setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            String[] actions = {
                    "30天情绪回顾",
                    "记录心情",
                    "AI 情绪洞察",
                    "导出报告",
                    "研究导出（匿名）",
                    "JSON 备份",
                    "JSON 恢复",
                    "清空记忆"
            };
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("更多操作")
                    .setItems(actions, (dialog, which) -> handleMoreAction(which))
                    .show();
        });
    }

    private void handleMoreAction(int actionIndex) {
        switch (actionIndex) {
            case 0:
                show30DayReview();
                break;
            case 1:
                showManualMoodDialog();
                break;
            case 2:
                generateAIInsight();
                break;
            case 3:
                exportHistoryData();
                break;
            case 4:
                exportResearchData();
                break;
            case 5:
                exportJSON();
                break;
            case 6:
                importJSON();
                break;
            case 7:
                clearHistory();
                break;
            default:
                throw new IllegalArgumentException("Unknown history action: " + actionIndex);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 手动心情打卡
    // ═══════════════════════════════════════════════════════════════
    private void showManualMoodDialog() {
        MoodDialogHelper.showMoodPicker(requireContext(), true, true,
                getString(R.string.mood_picker_title), (index, label, tag, note) -> {
                    String detail = "心情: " + label;
                    if (!tag.isEmpty()) {
                        detail += " #" + tag;
                    }
                    if (!note.isEmpty()) {
                        detail += "\n备注: " + note;
                    }
                    saveToDatabase("手动记录", detail, MoodSelectionPolicy.isPositiveMood(index));
                    updateStreak();
                    loadHistoryData();
                    showSnackbar(getString(R.string.mood_picker_saved, label));
                    showManualMoodAiPrompt(detail);
                });
    }

    private void showManualMoodAiPrompt(String detail) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("AI 情绪解读")
                .setMessage("需要 AI 为你解读当前情绪状态吗？")
                .setPositiveButton("立即解读", (dialog, which) -> requestAiAnalysis(detail))
                .setNegativeButton("稍后", null)
                .show();
    }

    private void updateStreak() {
        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        StreakManager.updateAndGetStreak(prefs);
    }

    private void clearHistory() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_clear_title)
                .setMessage(R.string.dialog_clear_message)
                .setPositiveButton(R.string.dialog_clear_confirm, (d, w) -> {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    db.execSQL("DELETE FROM " + Constants.TABLE_RECORDS);
                    db.close();
                    loadHistoryData();
                })
                .setNegativeButton(getString(R.string.dialog_cancel), null).show();
    }

    // ═══════════════════════════════════════════════════════════════
    // B1: 手动记录 AI 解读
    // ═══════════════════════════════════════════════════════════════
    private void requestAiAnalysis(String detail) {
        // 尝试通过 Activity 调用 AI
        if (getActivity() instanceof MainActivity) {
            MainActivity act = (MainActivity) getActivity();
            // 从详情中提取心情和备注，构建分析文本
            String simplifiedDetail = detail.replace("\n", " ").replace("\"", "");
            act.requestManualMoodAnalysis(simplifiedDetail);
        } else {
            showSnackbar("AI 解读暂不可用");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 数据库操作
    // ═══════════════════════════════════════════════════════════════
    private void saveToDatabase(String type, String detail, boolean positive) {
        executor.execute(() -> dbHelper.saveRecord(type, detail, positive));
    }

    public void loadHistoryData() {
        if (chartContainer != null) chartContainer.removeAllViews();
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor statCursor = null, cursor = null;
            try {
                int dateFilter = vm.getDateFilter().getValue() != null ? vm.getDateFilter().getValue() : 0;
                int moodFilter = vm.getMoodFilter().getValue() != null ? vm.getMoodFilter().getValue() : 0;

                String whereClause = "1=1";
                String[] whereArgs = new String[]{};
                if (moodFilter == 1) whereClause = Constants.COL_POSITIVE + " = ?";
                else if (moodFilter == 2) whereClause = Constants.COL_POSITIVE + " = ?";
                if (moodFilter == 1 || moodFilter == 2) {
                    whereArgs = new String[]{moodFilter == 1 ? "1" : "0"};
                }

                int posCount = 0, negCount = 0;
                cursor = db.rawQuery("SELECT * FROM " + Constants.TABLE_RECORDS
                        + " WHERE " + whereClause + " ORDER BY " + Constants.COL_ID + " DESC", whereArgs);
                List<Float> chartData = new ArrayList<>();
                List<String[]> rows = new ArrayList<>();

                while (cursor.moveToNext()) {
                    String time = cursor.getString(1);
                    String type = cursor.getString(2);
                    String detail = cursor.getString(3);
                    boolean isPos = cursor.getInt(4) == 1;
                    if (!matchesDateFilter(time, dateFilter)) continue;
                    if (isPos) posCount++; else negCount++;
                    float moodScore = calculateMoodScore(detail, isPos);
                    chartData.add(moodScore);
                    rows.add(new String[]{time, type, detail, String.valueOf(isPos)});
                }

                final int fPosCount = posCount, fNegCount = negCount;
                final List<Float> fChartData = chartData;
                final boolean isEmpty = rows.isEmpty();
                // 计算平均情绪分
                float avgScore = -1f;
                if (!chartData.isEmpty()) {
                    float sum = 0f;
                    for (float s : chartData) sum += s;
                    avgScore = sum / chartData.size();
                }

                Collections.reverse(fChartData);

                final float fAvgScore = avgScore;
                requireActivity().runOnUiThread(() -> {
                    vm.setHistoryData(rows, fPosCount + fNegCount, fPosCount, fNegCount,
                            fChartData, fAvgScore, isEmpty);
                    boolean hasTrend = fChartData.size() >= 3;
                    if (tvChartEmpty != null) {
                        tvChartEmpty.setVisibility(hasTrend ? View.GONE : View.VISIBLE);
                    }
                    if (hasTrend && chartContainer != null) {
                        // 收集日期标签
                        List<String> dates = new ArrayList<>();
                        for (String[] row : rows) dates.add(row[0]);
                        Collections.reverse(dates);
                        chartContainer.addView(new EmoLineChartView(requireContext(), fChartData, dates));
                    }
                    vm.setRefreshing(false);
                });
            } finally {
                if (statCursor != null) statCursor.close();
                if (cursor != null) cursor.close();
                db.close();
            }
        });
    }

    private float calculateMoodScore(String detail, boolean fallbackIsPos) {
        float score = fallbackIsPos ? 75f : 25f;
        try {
            Matcher m = Pattern.compile("(愉悦|平静|低落|紧绷|惊恐|疲惫)\\s+(\\d+)%").matcher(detail);
            float totalWeight = 0f, totalPerc = 0f;
            while (m.find()) {
                String emotion = m.group(1);
                float perc = Float.parseFloat(m.group(2));
                float weight = 50f;
                if (emotion.equals("愉悦")) weight = 95f;
                else if (emotion.equals("平静")) weight = 70f;
                else if (emotion.equals("疲惫")) weight = 45f;
                else if (emotion.equals("低落")) weight = 30f;
                else if (emotion.equals("紧绷")) weight = 20f;
                else if (emotion.equals("惊恐")) weight = 5f;
                totalWeight += weight * perc;
                totalPerc += perc;
            }
            if (totalPerc > 0) score = totalWeight / totalPerc;
        } catch (Exception e) { /* fallback */ }
        return Math.max(5f, Math.min(95f, score));
    }

    private boolean matchesDateFilter(String time, int dateFilter) {
        if (dateFilter == 1) {
            return EmoDatabaseHelper.isSameDay(time, new Date());
        }
        if (dateFilter == 2) {
            return EmoDatabaseHelper.isWithinLastDays(time, 7);
        }
        if (dateFilter == 3) {
            return EmoDatabaseHelper.isWithinLastDays(time, 30);
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // 导出
    // ═══════════════════════════════════════════════════════════════
    private void exportHistoryData() {
        String[] formats = {"纯文本 (.txt)", "CSV 表格 (.csv)", "Markdown 报告 (.md)"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.export_format_title)
                .setItems(formats, (dialog, which) -> chooseExportRange(which))
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show();
    }

    private void exportResearchData() {
        String[] formats = {"匿名 JSON（研究包）", "匿名 CSV（表格）"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("研究数据导出")
                .setMessage("导出会移除手机号、邮箱和身份证号，并按日期分桶；仅用于课程研究或自我复盘，不用于诊断。")
                .setItems(formats, (dialog, which) -> executeResearchExport(which))
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show();
    }

    private void executeResearchExport(int format) {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            try {
                List<ResearchDataExporter.Record> rows = HistoryExportRepository.loadResearchRows(db);

                if (rows.isEmpty()) {
                    runOnUiThreadSafe(() -> showSnackbar("暂无可导出的研究数据"));
                    return;
                }

                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                HistoryExportRepository.ExportData export = HistoryExportRepository.buildResearchExport(
                        rows, format == 0, ts);
                String reportStr = export.content;
                String fileName = export.fileName;
                String mimeType = export.mimeType;
                String savedPath = saveExportFile(fileName, reportStr);

                runOnUiThreadSafe(() -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType(mimeType);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, fileName);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, reportStr);
                    if (savedPath != null) {
                        java.io.File file = new java.io.File(savedPath);
                        Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                                requireContext(),
                                requireContext().getPackageName() + ".fileprovider", file);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    startActivity(Intent.createChooser(shareIntent, "分享匿名研究数据"));
                    if (savedPath != null) showSnackbar("匿名研究数据已保存");
                });
            } finally {
                db.close();
            }
        });
    }

    private void chooseExportRange(int format) {
        String[] ranges = {"最近 7 天", "最近 30 天", "全部记录"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择时间范围")
                .setItems(ranges, (dialog, which) -> {
                    int days = which == 0 ? 7 : (which == 1 ? 30 : 0);
                    executeExport(format, days);
                })
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show();
    }

    private void executeExport(int format, int days) {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            try {
                List<HistoryExportFormatter.Record> rows = HistoryExportRepository.loadRows(db, days);
                if (rows.isEmpty()) {
                    runOnUiThreadSafe(() -> showSnackbar("报告生成失败：暂无情绪记录"));
                    return;
                }

                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String generatedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                HistoryExportRepository.ExportData export = HistoryExportRepository.buildExport(
                        rows, format, ts, generatedAt);
                String savedPath = saveExportFile(export.fileName, export.content);
                final String finalReport = export.content;
                final String finalFileName = export.fileName;
                final String finalMimeType = export.mimeType;
                final String finalSavedPath = savedPath;

                runOnUiThreadSafe(() -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType(finalMimeType);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, finalFileName);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, finalReport);

                    if (finalSavedPath != null) {
                        java.io.File file = new java.io.File(finalSavedPath);
                        Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                                requireContext(),
                                requireContext().getPackageName() + ".fileprovider", file);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        shareIntent.setType("text/plain");
                    }

                    startActivity(Intent.createChooser(shareIntent, getString(R.string.export_chooser_title)));
                    if (finalSavedPath != null) {
                        showSnackbar(getString(R.string.export_saved));
                    }
                });
            } finally {
                db.close();
            }
        });
    }

    private String saveExportFile(String fileName, String content) {
        try {
            java.io.File dir = new java.io.File(requireContext().getExternalFilesDir(null), "Exports");
            java.io.File file = HistoryExportRepository.writeExport(dir,
                    new HistoryExportRepository.ExportData(fileName, "", content));
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void runOnUiThreadSafe(Runnable action) {
        if (!isAdded() || getActivity() == null) return;
        requireActivity().runOnUiThread(() -> {
            if (isAdded()) action.run();
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // AI 情绪洞察 (A3)
    // ═══════════════════════════════════════════════════════════════
    private void generateAIInsight() {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT * FROM " + Constants.TABLE_RECORDS
                        + " ORDER BY " + Constants.COL_ID + " DESC LIMIT 30", null);
                List<String[]> rows = new ArrayList<>();
                while (cursor.moveToNext()) {
                    rows.add(new String[]{cursor.getString(1), cursor.getString(2),
                            cursor.getString(3), cursor.getString(4)});
                }
                if (rows.isEmpty()) {
                    requireActivity().runOnUiThread(() -> showSnackbar("暂无足够数据生成洞察"));
                    return;
                }

                // 本地统计分析
                int posCount = 0, negCount = 0;
                float totalScore = 0;
                for (String[] row : rows) {
                    boolean isPos = "1".equals(row[3]);
                    if (isPos) posCount++; else negCount++;
                    totalScore += calculateMoodScore(row[2], isPos);
                }
                float avgScore = totalScore / rows.size();
                float posRatio = (float) posCount / rows.size() * 100;

                // 常见情绪关键词统计
                java.util.Map<String, Integer> emotionCounts = new java.util.HashMap<>();
                for (String emo : Constants.EMOTION_NAMES) emotionCounts.put(emo, 0);
                for (String[] row : rows) {
                    for (String emo : Constants.EMOTION_NAMES) {
                        if (row[2].contains(emo)) emotionCounts.put(emo, emotionCounts.get(emo) + 1);
                    }
                }
                StringBuilder topEmotions = new StringBuilder();
                emotionCounts.entrySet().stream()
                        .filter(e -> e.getValue() > 0)
                        .sorted((a, b) -> b.getValue() - a.getValue())
                        .limit(3)
                        .forEach(e -> topEmotions.append(e.getKey()).append("(").append(e.getValue()).append("次) "));

                // 非诊断性表述：用"你可能会注意到"替代"[良好/波动/偏低]诊断式标签
                String gentleHint;
                if (avgScore >= 65) {
                    gentleHint = "你可能注意到：这段时间整体状态比较平稳。保持现在的生活节奏就好。";
                } else if (avgScore >= 40) {
                    gentleHint = "你可能注意到：情绪有些起伏，这在生活中很常见。给自己多一些空间和时间，做一件让你感到放松的小事。";
                } else {
                    gentleHint = "你可能注意到：这段时间情绪偏低。这并不说明你有什么“问题”，只是提醒你可以多接触阳光、自然，或者联系你信任的人聊一聊。";
                }
                String insight = String.format(Locale.getDefault(),
                        "近期情绪回顾\n\n"
                        + "回顾样本：最近 %d 条记录\n"
                        + "平均情绪分：%.0f / 100\n"
                        + "平稳占比：%.0f%% (%d条)\n"
                        + "常出现的情绪：%s\n\n"
                        + "%s\n\n"
                        + "提醒：这不是诊断，只是帮你回看。如果持续感到困扰，可以考虑联系专业资源或信任的人。",
                        rows.size(), avgScore, posRatio, posCount,
                        topEmotions.toString().trim(),
                        gentleHint);

                final String finalInsight = insight;
                requireActivity().runOnUiThread(() -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("AI 情绪洞察")
                            .setMessage(finalInsight)
                            .setPositiveButton("知道了", null)
                            .show();
                });
            } finally {
                if (cursor != null) cursor.close();
                db.close();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 30天情绪回顾 — 调用 AiMemoryEngine 本地分析
    // ═══════════════════════════════════════════════════════════════
    private void show30DayReview() {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            try {
                AiMemoryEngine.MemoryResult result = AiMemoryEngine.analyze(db);
                if (result.totalRecords < 3) {
                    requireActivity().runOnUiThread(() ->
                            showSnackbar("需要至少 3 条记录才能生成回顾，继续记录吧 🌱"));
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("回溯样本：最近 30 天，共 ").append(result.totalRecords).append(" 条\n");
                sb.append("情绪均值：").append(String.format("%.0f", result.avgScore)).append(" / 100\n");
                sb.append("平稳占比：").append(String.format("%.0f", result.positivityRatio)).append("%\n");
                if (result.streakDays > 1) {
                    sb.append("连续记录：").append(result.streakDays).append(" 天\n");
                }
                sb.append("\n");
                if (result.dominantEmotion != null && !result.dominantEmotion.isEmpty()) {
                    sb.append("这段时间最常出现的情绪线索：").append(result.dominantEmotion).append("\n");
                }
                if (result.weeklyTrendDesc != null && !result.weeklyTrendDesc.isEmpty()) {
                    sb.append("近两周趋势：").append(result.weeklyTrendDesc).append("\n\n");
                }
                if (!result.topStressSources.isEmpty()) {
                    sb.append("你可能在意的：\n");
                    int r = 1;
                    for (AiMemoryEngine.KeywordCount kc : result.topStressSources) {
                        sb.append("  ").append(r++).append(". ").append(kc.keyword)
                                .append("（").append(kc.count).append("次）\n");
                        if (r > 3) break;
                    }
                    sb.append("\n");
                }
                if (!result.topJoySources.isEmpty()) {
                    sb.append("让你开心的事：\n");
                    int r = 1;
                    for (AiMemoryEngine.KeywordCount kc : result.topJoySources) {
                        sb.append("  ").append(r++).append(". ").append(kc.keyword)
                                .append("（").append(kc.count).append("次）\n");
                        if (r > 3) break;
                    }
                }
                sb.append("\n提醒：这只是基于记录的回看，不是诊断。数据只保存在你的手机上。");

                final String reviewText = sb.toString();
                requireActivity().runOnUiThread(() -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("30天情绪回顾")
                            .setMessage(reviewText)
                            .setPositiveButton("谢谢，我看到了", null)
                            .show();
                });
            } finally {
                db.close();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // JSON 备份/恢复 (B2)
    // ═══════════════════════════════════════════════════════════════
    private void exportJSON() {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            try {
                List<com.example.emoscope.history.HistoryBackupFormatter.Record> rows =
                        HistoryExportRepository.loadBackupRows(db);

                java.io.File dir = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS), AppBrand.EXPORT_DIRECTORY);
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                java.io.File file = HistoryExportRepository.writeExport(dir,
                        new HistoryExportRepository.ExportData(AppBrand.backupFileName(ts),
                                "application/json", HistoryBackupFormatter.buildJson(rows)));

                final String path = file.getAbsolutePath();
                final int recordCount = rows.size();
                requireActivity().runOnUiThread(() -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("备份完成")
                            .setMessage("已保存 " + recordCount + " 条记录到：\n" + path)
                            .setPositiveButton("知道了", null)
                            .show();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> showSnackbar("备份失败：" + e.getMessage()));
            } finally {
                db.close();
            }
        });
    }

    private void importJSON() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("恢复备份")
                .setMessage(getString(R.string.import_json_confirm))
                .setPositiveButton("确认恢复", (d, w) -> doImportJSON())
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show();
    }

    private void doImportJSON() {
        executor.execute(() -> {
            try {
                java.io.File downloads = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS);
                java.io.File dir = HistoryExportRepository.selectImportDirectory(downloads);
                if (!dir.exists() || dir.listFiles() == null) {
                    requireActivity().runOnUiThread(() -> showSnackbar(getString(R.string.import_json_empty)));
                    return;
                }
                java.io.File latest = HistoryExportRepository.findLatestJsonBackup(dir);
                if (latest == null) {
                    requireActivity().runOnUiThread(() -> showSnackbar(getString(R.string.import_json_empty)));
                    return;
                }
                // 取最新的备份文件
                String content = HistoryExportRepository.readUtf8(latest);
                int count;
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                try {
                    count = HistoryExportRepository.importBackup(db, content);
                } finally {
                    db.close();
                }

                final int importedCount = count;
                requireActivity().runOnUiThread(() -> {
                    showSnackbar(String.format(Locale.getDefault(),
                            getString(R.string.import_json_success), importedCount));
                    loadHistoryData();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> showSnackbar("恢复失败：" + e.getMessage()));
            }
        });
    }

    private void showSnackbar(String msg) {
        View v = getView();
        if (v != null) Snackbar.make(v, msg, Snackbar.LENGTH_SHORT).show();
    }
}
