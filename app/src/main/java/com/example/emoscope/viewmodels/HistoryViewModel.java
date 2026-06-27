package com.example.emoscope.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆流页 ViewModel — 持有历史记录列表、统计数据、筛选状态，
 * 配置变更时自动保留已加载数据，避免重复查询数据库。
 */
public class HistoryViewModel extends ViewModel {

    // ── 记录列表 ──
    private final MutableLiveData<List<String[]>> historyItems = new MutableLiveData<>(new ArrayList<>());

    // ── 统计数据 ──
    private final MutableLiveData<Integer> statTotal = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> statPos = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> statNeg = new MutableLiveData<>(0);
    private final MutableLiveData<Float> statAvg = new MutableLiveData<>(-1f); // 平均情绪分

    // ── 筛选状态 ──
    private final MutableLiveData<Integer> dateFilter = new MutableLiveData<>(0); // 0=全部, 2=7天, 3=30天
    private final MutableLiveData<Integer> moodFilter = new MutableLiveData<>(0); // 0=全部, 1=积极, 2=关注

    // ── 图表数据 ──
    private final MutableLiveData<List<Float>> chartData = new MutableLiveData<>(new ArrayList<>());

    // ── 空状态 ──
    private final MutableLiveData<Boolean> isEmpty = new MutableLiveData<>(true);

    // ── 刷新状态 ──
    private final MutableLiveData<Boolean> isRefreshing = new MutableLiveData<>(false);

    // ═══════════════════════════════════════════════════════════════
    public LiveData<List<String[]>> getHistoryItems() { return historyItems; }
    public LiveData<Integer> getStatTotal() { return statTotal; }
    public LiveData<Integer> getStatPos() { return statPos; }
    public LiveData<Integer> getStatNeg() { return statNeg; }
    public LiveData<Float> getStatAvg() { return statAvg; }
    public LiveData<Integer> getDateFilter() { return dateFilter; }
    public LiveData<Integer> getMoodFilter() { return moodFilter; }
    public LiveData<List<Float>> getChartData() { return chartData; }
    public LiveData<Boolean> getIsEmpty() { return isEmpty; }
    public LiveData<Boolean> getIsRefreshing() { return isRefreshing; }

    public void setHistoryData(List<String[]> items, int total, int pos, int neg,
                                List<Float> chart, float avgScore, boolean empty) {
        historyItems.setValue(items);
        statTotal.setValue(total);
        statPos.setValue(pos);
        statNeg.setValue(neg);
        statAvg.setValue(avgScore);
        chartData.setValue(chart);
        isEmpty.setValue(empty);
    }

    public void setDateFilter(int filter) { dateFilter.setValue(filter); }
    public void setMoodFilter(int filter) { moodFilter.setValue(filter); }
    public void setRefreshing(boolean refreshing) { isRefreshing.setValue(refreshing); }
}
