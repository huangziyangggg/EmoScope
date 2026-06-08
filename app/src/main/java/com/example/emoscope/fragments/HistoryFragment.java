package com.example.emoscope.fragments;

import android.app.AlertDialog;
import android.content.ContentValues;
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
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.emoscope.Constants;
import com.example.emoscope.EmoDatabaseHelper;
import com.example.emoscope.EmoLineChartView;
import com.example.emoscope.HistoryAdapter;
import com.example.emoscope.MainActivity;
import com.example.emoscope.R;
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
    private TextView tvHistoryEmpty, tvStatTotal, tvStatPos, tvStatNeg, tvStatAvg;
    private FrameLayout chartContainer;
    private View filterAll, filterWeek, filterMonth;
    private ImageView filterPos, filterWarn;
    private TextView tvNotifyTime; // 可能为 null（在 settings 中）

    private static final SimpleDateFormat SDF_DB = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private static final SimpleDateFormat SDF_DAY = new SimpleDateFormat("MM-dd", Locale.getDefault());
    private static final Object SDF_LOCK = new Object();

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
            tvHistoryEmpty.setVisibility(Boolean.TRUE.equals(empty) ? View.VISIBLE : View.GONE);
            rvHistory.setVisibility(Boolean.TRUE.equals(empty) ? View.GONE : View.VISIBLE);
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
                    ? MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, 0)
                    : MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
            loadHistoryData();
        });
        filterWarn.setOnClickListener(view -> {
            int current = vm.getMoodFilter().getValue() != null ? vm.getMoodFilter().getValue() : 0;
            int next = (current == 2) ? 0 : 2;
            vm.setMoodFilter(next);
            filterWarn.setColorFilter(next == 2
                    ? MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorError, 0)
                    : MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
            loadHistoryData();
        });
    }

    private void updateFilterUI(View selected, View... others) {
        ((TextView) selected).setTextColor(MaterialColors.getColor(requireContext(),
                com.google.android.material.R.attr.colorPrimary, 0));
        for (View v : others) {
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
            PopupMenu popup = new PopupMenu(requireContext(), view);
            popup.getMenu().add("记录心情");
            popup.getMenu().add("AI 情绪洞察");
            popup.getMenu().add("导出报告");
            popup.getMenu().add("JSON 备份");
            popup.getMenu().add("JSON 恢复");
            popup.getMenu().add("清空记忆");
            popup.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if (title.contains("记录")) showManualMoodDialog();
                else if (title.contains("洞察")) generateAIInsight();
                else if (title.contains("导出")) exportHistoryData();
                else if (title.contains("备份")) exportJSON();
                else if (title.contains("恢复")) importJSON();
                else if (title.contains("清空")) clearHistory();
                return true;
            });
            popup.show();
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 手动心情打卡
    // ═══════════════════════════════════════════════════════════════
    private void showManualMoodDialog() {
        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(32, 24, 32, 16);

        final int[] selectedIdx = {-1};

        for (int row = 0; row < 2; row++) {
            LinearLayout rowLayout = new LinearLayout(requireContext());
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(android.view.Gravity.CENTER);
            for (int col = 0; col < 4; col++) {
                int idx = row * 4 + col;
                LinearLayout item = new LinearLayout(requireContext());
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(android.view.Gravity.CENTER);
                item.setPadding(16, 8, 16, 8);
                item.setClickable(true);
                item.setBackgroundColor(0x00000000);

                ImageView icon = new ImageView(requireContext());
                icon.setImageResource(Constants.MANUAL_MOOD_ICONS[idx]);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        (int) (40 * getResources().getDisplayMetrics().density),
                        (int) (40 * getResources().getDisplayMetrics().density));
                icon.setLayoutParams(iconParams);

                TextView label = new TextView(requireContext());
                label.setText(Constants.MANUAL_MOOD_LABELS[idx]);
                label.setTextSize(12);
                label.setTextColor(MaterialColors.getColor(requireContext(),
                        com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                label.setGravity(android.view.Gravity.CENTER);
                label.setPadding(0, 4, 0, 0);

                int finalIdx = idx;
                item.setOnClickListener(v -> {
                    selectedIdx[0] = finalIdx;
                    for (int i = 0; i < grid.getChildCount(); i++) {
                        LinearLayout r = (LinearLayout) grid.getChildAt(i);
                        for (int j = 0; j < r.getChildCount(); j++) {
                            r.getChildAt(j).setBackgroundColor(0x00000000);
                        }
                    }
                    item.setBackgroundColor(0x206C5CE7);
                });

                item.addView(icon);
                item.addView(label);
                rowLayout.addView(item, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
            grid.addView(rowLayout);
        }

        final EditText noteInput = new EditText(requireContext());
        noteInput.setHint("添加备注（可选）");
        noteInput.setSingleLine(true);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.setMargins(16, 12, 16, 0);
        grid.addView(noteInput, noteParams);

        // 标签选择 (B3)
        final String[] selectedTag = {""};
        LinearLayout tagRow = new LinearLayout(requireContext());
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        tagRow.setPadding(16, 8, 16, 0);
        tagRow.setGravity(android.view.Gravity.CENTER);
        for (String tag : Constants.EMOTION_TAGS) {
            TextView tagView = new TextView(requireContext());
            tagView.setText(tag);
            tagView.setTextSize(11);
            tagView.setPadding(12, 6, 12, 6);
            tagView.setTextColor(MaterialColors.getColor(requireContext(),
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
            tagView.setBackgroundResource(R.drawable.filter_pill);
            tagView.setClickable(true);
            tagView.setOnClickListener(v -> {
                if (tag.equals(selectedTag[0])) {
                    selectedTag[0] = "";
                    tagView.setBackgroundResource(R.drawable.filter_pill);
                    tagView.setTextColor(MaterialColors.getColor(requireContext(),
                            com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                } else {
                    // 清除所有选中
                    for (int i = 0; i < tagRow.getChildCount(); i++) {
                        tagRow.getChildAt(i).setBackgroundResource(R.drawable.filter_pill);
                        ((TextView) tagRow.getChildAt(i)).setTextColor(MaterialColors.getColor(requireContext(),
                                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                    }
                    selectedTag[0] = tag;
                    tagView.setBackgroundResource(R.drawable.filter_pill_selected);
                    tagView.setTextColor(MaterialColors.getColor(requireContext(),
                            com.google.android.material.R.attr.colorPrimary, 0));
                }
            });
            LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tagParams.setMargins(0, 0, 8, 0);
            tagRow.addView(tagView, tagParams);
        }
        grid.addView(tagRow);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("记录此刻心情")
                .setView(grid)
                .setPositiveButton("保存", (dialog, which) -> {
                    if (selectedIdx[0] < 0) {
                        showSnackbar("请先选择一个心情");
                        return;
                    }
                    String label = Constants.MANUAL_MOOD_LABELS[selectedIdx[0]];
                    String note = noteInput.getText().toString().trim();
                    String detail = "心情: " + label;
                    if (!selectedTag[0].isEmpty()) detail += " #" + selectedTag[0];
                    if (!note.isEmpty()) detail += "\n备注: " + note;
                    boolean isPos = selectedIdx[0] <= 2;
                    final String finalDetail = detail;

                    saveToDatabase("手动记录", detail, isPos);
                    updateStreak();
                    loadHistoryData();
                    showSnackbar("已记录: " + label);

                    // 询问是否需要 AI 解读 (B1)
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("AI 情绪解读")
                            .setMessage("需要 AI 为你解读当前情绪状态吗？")
                            .setPositiveButton("立即解读", (d2, w2) -> requestAiAnalysis(finalDetail))
                            .setNegativeButton("稍后", null)
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateStreak() {
        Context ctx = requireContext();
        String today;
        synchronized (SDF_LOCK) { today = SDF_DAY.format(new Date()); }
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String lastDate = prefs.getString(Constants.KEY_LAST_RECORD_DATE, "");
        int streak = prefs.getInt(Constants.KEY_STREAK_COUNT, 0);

        if (today.equals(lastDate)) return;
        if (!lastDate.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
                Date last = sdf.parse(lastDate);
                Date now = sdf.parse(today);
                long diff = (now.getTime() - last.getTime()) / (1000 * 60 * 60 * 24);
                if (diff == 1) streak++;
                else streak = 1;
            } catch (Exception e) { streak = 1; }
        } else {
            streak = 1;
        }

        prefs.edit().putString(Constants.KEY_LAST_RECORD_DATE, today)
                .putInt(Constants.KEY_STREAK_COUNT, streak).apply();
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
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            synchronized (SDF_LOCK) {
                values.put(Constants.COL_TIME, SDF_DB.format(new Date()));
            }
            values.put(Constants.COL_TYPE, type);
            values.put(Constants.COL_DETAIL, detail);
            values.put(Constants.COL_POSITIVE, positive ? 1 : 0);
            db.insert(Constants.TABLE_RECORDS, null, values);
            db.close();
        });
    }

    public void loadHistoryData() {
        if (chartContainer != null) chartContainer.removeAllViews();
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor statCursor = null, cursor = null;
            try {
                int dateFilter = vm.getDateFilter().getValue() != null ? vm.getDateFilter().getValue() : 0;
                int moodFilter = vm.getMoodFilter().getValue() != null ? vm.getMoodFilter().getValue() : 0;

                String dateWhere;
                String[] dateArgs;
                String today;
                synchronized (SDF_LOCK) { today = SDF_DAY.format(new Date()); }
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, 1);
                String tomorrow;
                synchronized (SDF_LOCK) { tomorrow = SDF_DAY.format(cal.getTime()); }

                if (dateFilter == 1) {
                    dateWhere = Constants.COL_TIME + " >= ? AND " + Constants.COL_TIME + " < ?";
                    dateArgs = new String[]{today, tomorrow};
                } else if (dateFilter == 2) {
                    cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_MONTH, -7);
                    String weekAgo;
                    synchronized (SDF_LOCK) { weekAgo = SDF_DAY.format(cal.getTime()); }
                    dateWhere = Constants.COL_TIME + " >= ?";
                    dateArgs = new String[]{weekAgo};
                } else if (dateFilter == 3) {
                    cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_MONTH, -30);
                    String monthAgo;
                    synchronized (SDF_LOCK) { monthAgo = SDF_DAY.format(cal.getTime()); }
                    dateWhere = Constants.COL_TIME + " >= ?";
                    dateArgs = new String[]{monthAgo};
                } else {
                    dateWhere = "1=1";
                    dateArgs = new String[]{};
                }

                String moodWhere = "";
                if (moodFilter == 1) moodWhere = " AND " + Constants.COL_POSITIVE + " = 1";
                else if (moodFilter == 2) moodWhere = " AND " + Constants.COL_POSITIVE + " = 0";

                String whereClause = dateWhere + moodWhere;
                String[] whereArgs = dateArgs.length > 0 ? dateArgs : new String[]{};

                statCursor = db.rawQuery("SELECT " + Constants.COL_POSITIVE + ", COUNT(*) FROM "
                        + Constants.TABLE_RECORDS + " WHERE " + whereClause
                        + " GROUP BY " + Constants.COL_POSITIVE, whereArgs);
                int posCount = 0, negCount = 0;
                while (statCursor.moveToNext()) {
                    if (statCursor.getInt(0) == 1) posCount = statCursor.getInt(1);
                    else negCount = statCursor.getInt(1);
                }
                statCursor.close(); statCursor = null;

                cursor = db.rawQuery("SELECT * FROM " + Constants.TABLE_RECORDS
                        + " WHERE " + whereClause + " ORDER BY " + Constants.COL_ID + " DESC", whereArgs);
                List<Float> chartData = new ArrayList<>();
                List<String[]> rows = new ArrayList<>();

                while (cursor.moveToNext()) {
                    String time = cursor.getString(1);
                    String type = cursor.getString(2);
                    String detail = cursor.getString(3);
                    boolean isPos = cursor.getInt(4) == 1;
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
                    if (!fChartData.isEmpty() && chartContainer != null) {
                        // 收集日期标签
                        List<String> dates = new ArrayList<>();
                        for (String[] row : rows) dates.add(row[0]);
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
            Cursor cursor = null;
            try {
                String whereClause = "";
                String[] whereArgs = new String[]{};
                if (days > 0) {
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_MONTH, -days);
                    String dayAgo;
                    synchronized (SDF_LOCK) { dayAgo = SDF_DAY.format(cal.getTime()); }
                    whereClause = " WHERE " + Constants.COL_TIME + " >= ?";
                    whereArgs = new String[]{dayAgo};
                }
                cursor = db.rawQuery("SELECT * FROM " + Constants.TABLE_RECORDS + whereClause
                        + " ORDER BY " + Constants.COL_ID + " DESC", whereArgs);

                List<String[]> rows = new ArrayList<>();
                while (cursor.moveToNext()) {
                    rows.add(new String[]{
                            cursor.getString(1), cursor.getString(2),
                            cursor.getString(3), cursor.getString(4)
                    });
                }
                if (rows.isEmpty()) {
                    requireActivity().runOnUiThread(() -> showSnackbar("报告生成失败：暂无情绪记录"));
                    return;
                }

                String reportStr;
                String fileName;
                String mimeType;
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                if (format == 0) {
                    reportStr = buildTextExport(rows);
                    fileName = "EmoScope_" + ts + ".txt";
                    mimeType = "text/plain";
                } else if (format == 1) {
                    reportStr = buildCsvExport(rows);
                    fileName = "EmoScope_" + ts + ".csv";
                    mimeType = "text/csv";
                } else {
                    reportStr = buildMarkdownExport(rows);
                    fileName = "EmoScope_" + ts + ".md";
                    mimeType = "text/markdown";
                }

                String savedPath = saveExportFile(fileName, reportStr);
                final String finalReport = reportStr;
                final String finalFileName = fileName;
                final String finalMimeType = mimeType;
                final String finalSavedPath = savedPath;

                requireActivity().runOnUiThread(() -> {
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
                if (cursor != null) cursor.close();
                db.close();
            }
        });
    }

    private String buildTextExport(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.export_report_header));
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
        sb.append("\n\n");
        int count = 0;
        for (String[] row : rows) {
            count++;
            boolean isPos = "1".equals(row[3]);
            sb.append("--- 样本 ").append(count).append(" ---\n")
                    .append("时刻: ").append(row[0]).append("\n")
                    .append("类型: ").append(row[1]).append("\n")
                    .append("判定: ").append(isPos ? "[积极/平稳]" : "[压力/预警]").append("\n")
                    .append("详情:\n").append(row[2]).append("\n\n");
        }
        return sb.toString();
    }

    private String buildCsvExport(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("﻿"); // BOM
        sb.append("时间,类型,情绪判定,详情\n");
        for (String[] row : rows) {
            boolean isPos = "1".equals(row[3]);
            String escapedDetail = "\"" + row[2].replace("\"", "\"\"")
                    .replace("\n", " / ") + "\"";
            sb.append(row[0]).append(",").append(row[1]).append(",")
                    .append(isPos ? "积极" : "预警").append(",")
                    .append(escapedDetail).append("\n");
        }
        return sb.toString();
    }

    private String buildMarkdownExport(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("# EmoScope 情绪分析报告\n\n");
        sb.append("> 生成时间: ");
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
        sb.append("\n\n| # | 时间 | 类型 | 情绪 | 详情 |\n");
        sb.append("|---|------|------|------|------|\n");
        int count = 0;
        for (String[] row : rows) {
            count++;
            boolean isPos = "1".equals(row[3]);
            String shortDetail = row[2].length() > 60
                    ? row[2].substring(0, 57).replace("\n", " ") + "..."
                    : row[2].replace("\n", " ");
            sb.append("| ").append(count).append(" | ").append(row[0])
                    .append(" | ").append(row[1])
                    .append(" | ").append(isPos ? "[积极]" : "[预警]")
                    .append(" | ").append(shortDetail).append(" |\n");
        }
        sb.append("\n> 共 ").append(rows.size()).append(" 条记录\n");
        return sb.toString();
    }

    private String saveExportFile(String fileName, String content) {
        try {
            java.io.File dir = new java.io.File(requireContext().getExternalFilesDir(null), "Exports");
            if (!dir.exists()) dir.mkdirs();
            java.io.File file = new java.io.File(dir, fileName);
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(content);
            writer.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
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

                String insight = String.format(Locale.getDefault(),
                        "[近期情绪洞察]\n\n"
                        + "分析样本：最近 %d 条记录\n"
                        + "平均情绪分：%.0f / 100\n"
                        + "积极比例：%.0f%% (%d条)\n"
                        + "常见情绪：%s\n\n"
                        + "%s",
                        rows.size(), avgScore, posRatio, posCount,
                        topEmotions.toString().trim(),
                        avgScore >= 65 ? "[良好] 整体情绪状态良好，保持积极的生活方式"
                                : avgScore >= 40 ? "[波动] 情绪有些波动，给自己多一些关爱和时间"
                                : "[偏低] 近期情绪偏低，建议多接触阳光和自然，必要时寻求支持");

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
    // JSON 备份/恢复 (B2)
    // ═══════════════════════════════════════════════════════════════
    private void exportJSON() {
        executor.execute(() -> {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT * FROM " + Constants.TABLE_RECORDS
                        + " ORDER BY " + Constants.COL_ID, null);
                org.json.JSONArray arr = new org.json.JSONArray();
                while (cursor.moveToNext()) {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("time", cursor.getString(1));
                    obj.put("type", cursor.getString(2));
                    obj.put("detail", cursor.getString(3));
                    obj.put("positive", cursor.getInt(4));
                    arr.put(obj);
                }

                java.io.File dir = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS), "EmoScope");
                if (!dir.exists()) dir.mkdirs();
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                java.io.File file = new java.io.File(dir, "EmoScope_Backup_" + ts + ".json");
                java.io.FileWriter writer = new java.io.FileWriter(file);
                writer.write(arr.toString(2));
                writer.close();

                final String path = file.getAbsolutePath();
                requireActivity().runOnUiThread(() -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("备份完成")
                            .setMessage("已保存 " + arr.length() + " 条记录到：\n" + path)
                            .setPositiveButton("知道了", null)
                            .show();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> showSnackbar("备份失败：" + e.getMessage()));
            } finally {
                if (cursor != null) cursor.close();
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
                java.io.File dir = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS), "EmoScope");
                if (!dir.exists() || dir.listFiles() == null) {
                    requireActivity().runOnUiThread(() -> showSnackbar(getString(R.string.import_json_empty)));
                    return;
                }
                java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
                if (files == null || files.length == 0) {
                    requireActivity().runOnUiThread(() -> showSnackbar(getString(R.string.import_json_empty)));
                    return;
                }
                // 取最新的备份文件
                java.io.File latest = files[0];
                for (java.io.File f : files) {
                    if (f.lastModified() > latest.lastModified()) latest = f;
                }

                String content = new String(java.nio.file.Files.readAllBytes(latest.toPath()));
                org.json.JSONArray arr = new org.json.JSONArray(content);

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    values.put(Constants.COL_TIME, obj.getString("time"));
                    values.put(Constants.COL_TYPE, obj.getString("type"));
                    values.put(Constants.COL_DETAIL, obj.getString("detail"));
                    values.put(Constants.COL_POSITIVE, obj.optInt("positive", 1));
                    db.insert(Constants.TABLE_RECORDS, null, values);
                }
                db.close();

                final int count = arr.length();
                requireActivity().runOnUiThread(() -> {
                    showSnackbar(String.format(Locale.getDefault(),
                            getString(R.string.import_json_success), count));
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
