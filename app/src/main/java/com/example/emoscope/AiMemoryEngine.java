package com.example.emoscope;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 记忆引擎 — 本地统计分析，不依赖网络。
 * 分析最近 30 天的情绪记录，输出压力来源、开心来源、情绪低谷等洞察。
 */
public final class AiMemoryEngine {

    private AiMemoryEngine() {}

    /** 分析结果 */
    public static class MemoryResult {
        public int totalRecords;
        public float avgScore;
        public float positivityRatio;
        public List<KeywordCount> topStressSources;    // 压力来源 Top3
        public List<KeywordCount> topJoySources;        // 开心来源 Top3
        public String lowestDay;                         // 情绪最低谷日期 "MM-dd"
        public float lowestScore;
        public String highestDay;                        // 情绪最高峰日期
        public float highestScore;
        public int streakDays;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("分析样本：最近 30 天，共 ").append(totalRecords).append(" 条记录\n");
            sb.append("情绪均值：").append(String.format("%.0f", avgScore)).append(" / 100\n");
            sb.append("积极占比：").append(String.format("%.0f", positivityRatio)).append("%\n\n");

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
                sb.append("情绪最低谷：").append(lowestDay)
                        .append("（").append(String.format("%.0f", lowestScore)).append("分）\n");
            }
            if (!highestDay.isEmpty()) {
                sb.append("情绪最高峰：").append(highestDay)
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

    // ── 情感模式关键词 ──
    private static final String[] EMOTION_KEYWORDS = {
        "愉悦", "开心", "高兴", "快乐",
        "低落", "难过", "伤心", "沮丧",
        "焦虑", "紧张", "担忧", "担心",
        "愤怒", "生气", "烦躁",
        "平静", "放松", "舒适", "安稳"
    };

    /**
     * 分析最近 30 天记录，生成 AI 记忆报告。
     * 调用方应在后台线程执行。
     */
    public static MemoryResult analyze(SQLiteDatabase db) {
        MemoryResult result = new MemoryResult();

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
        String thirtyDaysAgo = sdf.format(cal.getTime());

        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT " + Constants.COL_TIME + ", "
                    + Constants.COL_DETAIL + ", " + Constants.COL_POSITIVE
                    + " FROM " + Constants.TABLE_RECORDS
                    + " WHERE " + Constants.COL_TIME + " >= ?"
                    + " ORDER BY " + Constants.COL_ID,
                    new String[]{thirtyDaysAgo});

            if (cursor.getCount() < 3) {
                result.totalRecords = cursor.getCount();
                return result;
            }

            result.totalRecords = cursor.getCount();

            Map<String, Integer> stressCount = new HashMap<>();
            Map<String, Integer> joyCount = new HashMap<>();
            Map<String, Float> dayScores = new HashMap<>();

            float totalScore = 0;
            int posCount = 0;

            while (cursor.moveToNext()) {
                String time = cursor.getString(0);
                String detail = cursor.getString(1);
                boolean isPos = cursor.getInt(2) == 1;

                if (isPos) posCount++;
                float score = isPos ? 75f : 25f;

                // 从详情中解析情绪百分比
                score = parseMoodScore(detail, score);
                totalScore += score;

                // 提取日期（MM-dd）
                if (time != null && time.length() >= 5) {
                    String day = time.substring(0, 5);
                    float currentSum = dayScores.containsKey(day) ? dayScores.get(day) : 0;
                    int dayCount = 1;
                    dayScores.put(day, currentSum + score);
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

            result.positivityRatio = (float) posCount / result.totalRecords * 100;
            result.avgScore = totalScore / result.totalRecords;

            // 排序取 Top3
            result.topStressSources = getTop(stressCount, 3);
            result.topJoySources = getTop(joyCount, 3);

            // 找最低谷和最高峰
            if (!dayScores.isEmpty()) {
                Map.Entry<String, Float> low = Collections.min(dayScores.entrySet(),
                        Comparator.comparing(Map.Entry::getValue));
                Map.Entry<String, Float> high = Collections.max(dayScores.entrySet(),
                        Comparator.comparing(Map.Entry::getValue));
                result.lowestDay = low.getKey();
                result.lowestScore = low.getValue();
                result.highestDay = high.getKey();
                result.highestScore = high.getValue();
            }

        } finally {
            if (cursor != null) cursor.close();
        }

        return result;
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

    private static float parseMoodScore(String detail, float fallback) {
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
            if (totalPerc > 0) return Math.max(5f, Math.min(95f, totalWeight / totalPerc));
        } catch (Exception ignored) {}
        return fallback;
    }
}
