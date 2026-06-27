package com.example.emoscope;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 记忆引擎 — 本地统计分析，不依赖网络。
 * 分析最近 30 天的情绪记录，输出压力来源、开心来源、情绪低谷等洞察。
 */
public final class AiMemoryEngine {

    private AiMemoryEngine() {}

    /** 分析结果 — 包含横向关键词 + 纵向趋势 + 情感分布多维洞察 */
    public static class MemoryResult {
        public int totalRecords;
        public float avgScore;
        public float positivityRatio;
        public List<KeywordCount> topStressSources;
        public List<KeywordCount> topJoySources;
        public String lowestDay;
        public float lowestScore;
        public String highestDay;
        public float highestScore;
        public int streakDays;

        // 新增：主导情绪 + 周趋势
        public String dominantEmotion;
        public float thisWeekAvg;
        public float lastWeekAvg;
        public String weeklyTrendDesc;   // "上升中 ↑" / "平稳 →" / "下降中 ↓"

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("分析样本：最近 30 天，共 ").append(totalRecords).append(" 条记录\n");
            sb.append("近期概况：").append(String.format("%.0f", avgScore)).append(" / 100\n");
            sb.append("积极占比：").append(String.format("%.0f", positivityRatio)).append("%\n");

            if (streakDays > 1) {
                sb.append("连续打卡：").append(streakDays).append(" 天 🔥\n");
            }
            sb.append("\n");

            if (dominantEmotion != null && !dominantEmotion.isEmpty()) {
                sb.append("主导情绪：").append(dominantEmotion).append("\n");
            }
            if (weeklyTrendDesc != null && !weeklyTrendDesc.isEmpty()) {
                sb.append("周趋势：").append(weeklyTrendDesc)
                        .append("（上周均 ").append(String.format("%.0f", lastWeekAvg))
                        .append(" → 本周均 ").append(String.format("%.0f", thisWeekAvg)).append("）\n\n");
            }

            if (!topStressSources.isEmpty()) {
                sb.append("最常见压力来源：\n");
                int rank = 1;
                for (KeywordCount kc : topStressSources) {
                    sb.append("  ").append(rank++).append(". ").append(kc.keyword)
                            .append("（").append(kc.count).append("次）\n");
                }
                sb.append("\n");
            }

            if (!topJoySources.isEmpty()) {
                sb.append("最容易开心的时候：\n");
                int rank = 1;
                for (KeywordCount kc : topJoySources) {
                    sb.append("  ").append(rank++).append(". ").append(kc.keyword)
                            .append("（").append(kc.count).append("次）\n");
                }
                sb.append("\n");
            }

            if (!lowestDay.isEmpty()) {
                sb.append("波动较多记录：").append(lowestDay)
                        .append("（").append(String.format("%.0f", lowestScore)).append("分）\n");
            }
            if (!highestDay.isEmpty()) {
                sb.append("较平稳记录：").append(highestDay)
                        .append("（").append(String.format("%.0f", highestScore)).append("分）\n");
            }

            return sb.toString();
        }
    }

    public static class KeywordCount {
        public String keyword;
        public int count;
        public KeywordCount(String keyword, int count) {
            this.keyword = keyword;
            this.count = count;
        }
    }

    // ── 压力关键词库 ──
    private static final String[] STRESS_KEYWORDS = {
        "考试", "作业", "论文", "实验", "报告", "项目", "截止", "DDL", "ddl",
        "工作", "加班", "面试", "求职", "简历",
        "吵架", "分手", "失恋", "孤独", "失眠", "焦虑", "紧张", "累", "疲惫",
        "压力", "烦", "崩溃", "难", "失败", "拖延",
        "钱", "经济", "房租",
        "家庭", "父母", "吵架", "生病"
    };

    // ── 开心关键词库 ──
    private static final String[] JOY_KEYWORDS = {
        "游戏", "电影", "音乐", "运动", "健身", "跑步", "散步",
        "朋友", "聚会", "聚餐", "聊天", "逛街", "旅行",
        "完成", "成功", "进步", "学会", "通过",
        "美食", "好吃", "奶茶", "咖啡", "火锅",
        "睡觉", "休息", "放假", "周末",
        "收到", "礼物", "惊喜"
    };

    // ── 情绪权重映射（与 FaceAnalyzer 10 情绪对齐）──
    private static final java.util.Map<String, Float> EMOTION_WEIGHT_MAP = new java.util.HashMap<>();
    static {
        EMOTION_WEIGHT_MAP.put("愉悦", 95f);  EMOTION_WEIGHT_MAP.put("平静", 72f);
        EMOTION_WEIGHT_MAP.put("惊讶", 50f);  EMOTION_WEIGHT_MAP.put("轻蔑", 45f);
        EMOTION_WEIGHT_MAP.put("悲伤", 28f);  EMOTION_WEIGHT_MAP.put("焦虑", 22f);
        EMOTION_WEIGHT_MAP.put("愤怒", 15f);  EMOTION_WEIGHT_MAP.put("恐惧", 10f);
        EMOTION_WEIGHT_MAP.put("厌恶", 8f);   EMOTION_WEIGHT_MAP.put("疲惫", 35f);
    }

    /**
     * 分析最近 30 天记录，生成 AI 记忆报告。
     * 调用方应在后台线程执行。
     */
    public static MemoryResult analyze(SQLiteDatabase db) {
        MemoryResult result = new MemoryResult();

        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT " + Constants.COL_TIME + ", "
                    + Constants.COL_DETAIL + ", " + Constants.COL_POSITIVE
                    + " FROM " + Constants.TABLE_RECORDS
                    + " ORDER BY " + Constants.COL_ID + " DESC LIMIT 300", null);

            Map<String, Integer> stressCount = new HashMap<>();
            Map<String, Integer> joyCount = new HashMap<>();
            Map<String, Float> dayScores = new HashMap<>();
            Map<String, Integer> dayRecordCounts = new HashMap<>();

            float totalScore = 0;
            int posCount = 0;

            while (cursor.moveToNext()) {
                String time = cursor.getString(0);
                if (!EmoDatabaseHelper.isWithinLastDays(time, 30)) continue;

                String detail = cursor.getString(1);
                boolean isPos = cursor.getInt(2) == 1;

                result.totalRecords++;
                if (isPos) posCount++;
                float score = isPos ? 75f : 25f;

                // 从详情中解析情绪百分比
                score = parseMoodScore(detail, score);
                totalScore += score;

                // 提取日期（MM-dd）
                String day = normalizeDayKey(time);
                if (day != null) {
                    float currentSum = dayScores.containsKey(day) ? dayScores.get(day) : 0;
                    dayScores.put(day, currentSum + score);
                    dayRecordCounts.put(day, dayRecordCounts.getOrDefault(day, 0) + 1);
                }

                if (detail == null) continue;

                // 匹配关键词
                String lowerDetail = detail.toLowerCase();
                for (String kw : STRESS_KEYWORDS) {
                    if (lowerDetail.contains(kw)) {
                        stressCount.put(kw, stressCount.getOrDefault(kw, 0) + 1);
                    }
                }
                for (String kw : JOY_KEYWORDS) {
                    if (lowerDetail.contains(kw)) {
                        joyCount.put(kw, joyCount.getOrDefault(kw, 0) + 1);
                    }
                }
            }

            if (result.totalRecords < 3) {
                return result;
            }

            result.positivityRatio = (float) posCount / result.totalRecords * 100;
            result.avgScore = totalScore / result.totalRecords;

            // 连续打卡天数
            result.streakDays = computeStreakDays(dayRecordCounts);

            // 主导情绪 — 统计 detail 中最常出现的情绪名
            result.dominantEmotion = computeDominantEmotion(dayScores, dayRecordCounts);

            // 排序取 Top3
            result.topStressSources = getTop(stressCount, 3);
            result.topJoySources = getTop(joyCount, 3);

            // 计算日均分并找最低谷和最高峰
            Map<String, Float> avgDayScores = new HashMap<>();
            if (!dayScores.isEmpty()) {
                for (Map.Entry<String, Float> e : dayScores.entrySet()) {
                    int count = dayRecordCounts.getOrDefault(e.getKey(), 1);
                    avgDayScores.put(e.getKey(), e.getValue() / count);
                }
                Map.Entry<String, Float> low = Collections.min(avgDayScores.entrySet(),
                        Comparator.comparing(Map.Entry::getValue));
                Map.Entry<String, Float> high = Collections.max(avgDayScores.entrySet(),
                        Comparator.comparing(Map.Entry::getValue));
                result.lowestDay = low.getKey();
                result.lowestScore = low.getValue();
                result.highestDay = high.getKey();
                result.highestScore = high.getValue();
            }

            // 周趋势分析：本周均分 vs 上周均分
            computeWeeklyTrend(avgDayScores, result);

        } finally {
            if (cursor != null) cursor.close();
        }

        return result;
    }

    /**
     * 生成结构化周报数据，供 JSON 导出和 Canvas 周报渲染使用。
     * @return 包含均值、趋势、关键词、极值日期的 JSONObject，失败返回空对象。
     */
    public static JSONObject generateWeeklyReportData(SQLiteDatabase db) {
        MemoryResult result = analyze(db);
        JSONObject json = new JSONObject();
        try {
            json.put("totalRecords", result.totalRecords);
            json.put("avgScore", Math.round(result.avgScore));
            json.put("positivityRatio", Math.round(result.positivityRatio));
            json.put("streakDays", result.streakDays);
            if (result.dominantEmotion != null)
                json.put("dominantEmotion", result.dominantEmotion);
            if (result.weeklyTrendDesc != null)
                json.put("weeklyTrend", result.weeklyTrendDesc);
            json.put("thisWeekAvg", Math.round(result.thisWeekAvg));
            json.put("lastWeekAvg", Math.round(result.lastWeekAvg));
            json.put("lowestDay", result.lowestDay);
            json.put("lowestScore", Math.round(result.lowestScore));
            json.put("highestDay", result.highestDay);
            json.put("highestScore", Math.round(result.highestScore));

            JSONArray stressArr = new JSONArray();
            for (KeywordCount kc : result.topStressSources) {
                JSONObject item = new JSONObject();
                item.put("keyword", kc.keyword);
                item.put("count", kc.count);
                stressArr.put(item);
            }
            json.put("topStressSources", stressArr);

            JSONArray joyArr = new JSONArray();
            for (KeywordCount kc : result.topJoySources) {
                JSONObject item = new JSONObject();
                item.put("keyword", kc.keyword);
                item.put("count", kc.count);
                joyArr.put(item);
            }
            json.put("topJoySources", joyArr);
        } catch (Exception ignored) {}
        return json;
    }

    private static List<KeywordCount> getTop(Map<String, Integer> map, int n) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, (a, b) -> b.getValue() - a.getValue());
        List<KeywordCount> result = new ArrayList<>();
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            result.add(new KeywordCount(list.get(i).getKey(), list.get(i).getValue()));
        }
        return result;
    }

    /** 计算最近 30 天内的最大连续记录天数 */
    private static int computeStreakDays(Map<String, Integer> dayRecordCounts) {
        if (dayRecordCounts.isEmpty()) return 0;
        java.util.List<String> sortedDays = new ArrayList<>(dayRecordCounts.keySet());
        Collections.sort(sortedDays);
        int maxStreak = 0, currentStreak = 1;
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault());
        for (int i = 1; i < sortedDays.size(); i++) {
            try {
                java.util.Date prev = sdf.parse(sortedDays.get(i - 1));
                java.util.Date curr = sdf.parse(sortedDays.get(i));
                long diff = (curr.getTime() - prev.getTime()) / (1000 * 60 * 60 * 24);
                if (diff == 1) { currentStreak++; }
                else { maxStreak = Math.max(maxStreak, currentStreak); currentStreak = 1; }
            } catch (Exception ignored) {}
        }
        return Math.max(maxStreak, currentStreak);
    }

    /** 从日平均分中统计最频繁出现的主导情绪 */
    private static String computeDominantEmotion(Map<String, Float> dayScores,
                                                  Map<String, Integer> dayRecordCounts) {
        if (dayScores.isEmpty()) return "";
        // 取日均分最高的那天的情绪区间作为主导情绪参考
        float avg = 0;
        for (float s : dayScores.values()) avg += s;
        avg /= dayScores.size();
        if (avg >= 70) return "愉悦主导";
        if (avg >= 55) return "平静稳定";
        if (avg >= 40) return "轻度波动";
        if (avg >= 25) return "较多波动";
        return "起伏较多";
    }

    /** 计算本周 vs 上周均分趋势 */
    private static void computeWeeklyTrend(Map<String, Float> avgDayScores, MemoryResult result) {
        if (avgDayScores == null || avgDayScores.size() < 7) return;
        java.util.List<String> sorted = new ArrayList<>(avgDayScores.keySet());
        Collections.sort(sorted);
        int n = sorted.size();
        // 最近 7 天 vs 前 7 天
        int thisWeekStart = Math.max(0, n - 7);
        int lastWeekStart = Math.max(0, n - 14);
        float thisSum = 0, lastSum = 0;
        int thisCount = 0, lastCount = 0;
        for (int i = thisWeekStart; i < n; i++) {
            Float s = avgDayScores.get(sorted.get(i));
            if (s != null) { thisSum += s; thisCount++; }
        }
        for (int i = lastWeekStart; i < thisWeekStart; i++) {
            Float s = avgDayScores.get(sorted.get(i));
            if (s != null) { lastSum += s; lastCount++; }
        }
        if (thisCount > 0) result.thisWeekAvg = thisSum / thisCount;
        if (lastCount > 0) result.lastWeekAvg = lastSum / lastCount;
        float delta = result.thisWeekAvg - result.lastWeekAvg;
        if (delta > 8f) result.weeklyTrendDesc = "上升中 ↑";
        else if (delta < -8f) result.weeklyTrendDesc = "下降中 ↓";
        else result.weeklyTrendDesc = "平稳 →";
    }

    private static String normalizeDayKey(String time) {
        if (time == null) return null;
        if (time.length() >= 10 && time.charAt(4) == '-') {
            return time.substring(5, 10);
        }
        if (time.length() >= 5) {
            return time.substring(0, 5);
        }
        return null;
    }

    private static float parseMoodScore(String detail, float fallback) {
        try {
            // 匹配 FaceAnalyzer 输出的 "情绪名 XX%" 格式（覆盖全部 10 种情绪）
            String emotionNames = String.join("|", EMOTION_WEIGHT_MAP.keySet());
            Matcher m = Pattern.compile("(" + emotionNames + ")\\s+(\\d+)%").matcher(detail);
            float totalWeight = 0f, totalPerc = 0f;
            while (m.find()) {
                String emotion = m.group(1);
                float perc = Float.parseFloat(m.group(2));
                Float weight = EMOTION_WEIGHT_MAP.get(emotion);
                if (weight == null) weight = 50f;
                totalWeight += weight * perc;
                totalPerc += perc;
            }
            if (totalPerc > 0) return Math.max(5f, Math.min(95f, totalWeight / totalPerc));
        } catch (Exception ignored) {}
        return fallback;
    }
}
