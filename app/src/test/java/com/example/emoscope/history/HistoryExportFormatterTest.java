package com.example.emoscope.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HistoryExportFormatterTest {
    @Test
    public void buildsReadableTextReport() {
        List<HistoryExportFormatter.Record> rows = Arrays.asList(
                new HistoryExportFormatter.Record("2026-06-19 09:00", "手动记录", "心情: 平静", true),
                new HistoryExportFormatter.Record("2026-06-19 10:00", "语音记录", "心情: 紧张", false)
        );

        String text = HistoryExportFormatter.buildText(rows, "2026-06-19 14:00");

        assertTrue(text.startsWith("【心镜情绪分析报告】\n生成时间：2026-06-19 14:00"));
        assertTrue(text.contains("判定: [积极/平稳]"));
        assertTrue(text.contains("判定: [关注/压力]"));
    }

    @Test
    public void buildsCsvWithBomAndEscapedDetail() {
        List<HistoryExportFormatter.Record> rows = Arrays.asList(
                new HistoryExportFormatter.Record("2026-06-19 09:00", "手动记录",
                        "他说\"还好\"\n下一行", true)
        );

        String csv = HistoryExportFormatter.buildCsv(rows);

        assertTrue(csv.startsWith("\uFEFF时间,类型,情绪判定,详情\n"));
        assertEquals("\uFEFF时间,类型,情绪判定,详情\n"
                + "2026-06-19 09:00,手动记录,积极,\"他说\"\"还好\"\" / 下一行\"\n", csv);
    }

    @Test
    public void buildsMarkdownWithTruncatedLongDetail() {
        String longDetail = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        List<HistoryExportFormatter.Record> rows = Arrays.asList(
                new HistoryExportFormatter.Record("2026-06-19 09:00", "手动记录", longDetail, false)
        );

        String markdown = HistoryExportFormatter.buildMarkdown(rows, "2026-06-19 14:00");

        assertTrue(markdown.startsWith("# 心镜情绪分析报告\n\n> 生成时间: 2026-06-19 14:00"));
        assertTrue(markdown.contains("[关注]"));
        assertTrue(markdown.contains("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234..."));
        assertTrue(markdown.endsWith("> 共 1 条记录\n"));
    }
}
