# EmoScope Report Figure Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 生成一份内容边界清晰、具有七张科研风格图表且可直接提交的 EmoScope 结题报告。

**Architecture:** 以一个 Python/Matplotlib 图表模块生成所有科研图、导出 SVG/PDF/PNG；报告重建脚本仅负责将图表、题注和已核验的内容写入现有 Word 母版。Word 渲染后逐页检查并根据实际页码回写目录。

**Tech Stack:** Python 3.12、Matplotlib、Pillow、python-docx、LibreOffice。

## Global Constraints

- 图 1–6 的绘制、预览、导出和视觉核验只能使用 Python/Matplotlib。
- 不得构造准确率、样本量、显著性检验、用户研究或临床结论。
- 中文正文使用宋体、英文和数字使用 Times New Roman；文内参考文献保持右上角角标。
- 图表必须输出 SVG、PDF 和 600 dpi PNG，Word 中嵌入 PNG。
- 所有图和表均须有能脱离正文阅读的题注；界面截图只做裁切和排版，不修改屏幕内容。

---

### Task 1: 建立科研图表生成模块

**Files:**
- Create: `tmp/generate_scientific_report_figures.py`
- Create: `tmp/scientific_report_assets/`
- Test: `tmp/scientific_report_assets/manifest.json`

**Interfaces:**
- Consumes: 现有界面截图与终期报告的已实现模块清单。
- Produces: `fig01_iteration` 至 `fig07_interfaces` 的 SVG、PDF、PNG 文件和导出清单。

- [ ] **Step 1: 写出图表合同常量**

```python
FIGURE_CONTRACT = {
    "fig01_iteration": "终期系统将中期多源探索收敛为可维护的本地优先闭环",
    "fig02_architecture": "输入、端侧处理和反馈模块由本地数据链路连接",
    "fig03_evidence": "终期验收基于界面、模块入口与任务路径的可复核证据",
    "fig04_privacy": "外部服务仅在用户配置并主动发起时接收必要文本",
    "fig05_task_loop": "观察、记录、回顾和成长支持构成用户任务闭环",
    "fig06_mapping": "中期设想经工程取舍后形成有限且可验收的终期模块",
    "fig07_interfaces": "四个已实现界面覆盖核心任务入口",
}
```

- [ ] **Step 2: 实现 Matplotlib 主题与导出函数**

```python
def save_figure(fig, stem: str) -> dict[str, str]:
    fig.savefig(ASSET / f"{stem}.svg", bbox_inches="tight")
    fig.savefig(ASSET / f"{stem}.pdf", bbox_inches="tight")
    fig.savefig(ASSET / f"{stem}.png", dpi=600, bbox_inches="tight")
    return {"svg": str(ASSET / f"{stem}.svg"), "pdf": str(ASSET / f"{stem}.pdf"), "png": str(ASSET / f"{stem}.png")}
```

- [ ] **Step 3: 生成七张图和清单**

Run: `python tmp/generate_scientific_report_figures.py`

Expected: 每张图均存在 SVG、PDF、PNG；清单列出 21 个输出文件。

- [ ] **Step 4: 用 Python 打开 PNG 核验尺寸和像素**

```python
from PIL import Image
assert Image.open(ASSET / "fig01_iteration.png").width >= 2500
```

### Task 2: 回写结题报告的内容、图注和图表

**Files:**
- Modify: `tmp/rebuild_polished_report.py`
- Modify: `item/EmoScope项目结题报告（终期整合定稿）.docx`

**Interfaces:**
- Consumes: Task 1 的 PNG 图、现有报告母版和来源材料。
- Produces: 插入七张图、统一题注与表格样式的 Word 文档。

- [ ] **Step 1: 用科研图函数替换旧的 PIL 说明图函数**

```python
SCIENTIFIC_ASSET = ROOT / "tmp" / "scientific_report_assets"
iteration = SCIENTIFIC_ASSET / "fig01_iteration.png"
```

- [ ] **Step 2: 增加图 3、图 5、图 6 及对应正文段落**

```python
paragraph(doc, "为使验收结论可追溯，项目以任务路径、页面入口与本地数据链路形成证据闭环。")
add_figure(doc, evidence, "图 3 终期功能验证的证据链（资料来源：项目工程、界面与验收记录整理）", 16.2)
```

- [ ] **Step 3: 统一所有表格的表头、对齐、边距和跨页规则**

```python
for table in doc.tables:
    format_scientific_table(table)
```

- [ ] **Step 4: 重写摘要、终期取舍、验证范围和总结段落**

```python
paragraph(doc, "终期报告仅将工程与界面可核验的模块作为完成成果，未将阶段性模型设想包装为已验证结论。")
```

- [ ] **Step 5: 构建文档**

Run: `python tmp/rebuild_polished_report.py`

Expected: `item/EmoScope项目结题报告（终期整合定稿）.docx` 更新时间变更，且有 7 张内嵌图。

### Task 3: 渲染、逐页核验与目录校准

**Files:**
- Modify: `tmp/rebuild_polished_report.py`
- Create: `tmp/rendered_report/`

**Interfaces:**
- Consumes: Task 2 输出的 Word 文档。
- Produces: PDF、页面 PNG 和最终正确页码目录。

- [ ] **Step 1: 使用 LibreOffice 转换为 PDF，并用 Poppler 生成页面 PNG**

Run: `soffice --headless --convert-to pdf --outdir tmp/rendered_report item/EmoScope项目结题报告（终期整合定稿）.docx`

Expected: PDF 存在且页面 PNG 数量大于 30。

- [ ] **Step 2: 检查全部图页、全部表格页和附录页**

```python
assert all(page.exists() for page in required_page_pngs)
```

- [ ] **Step 3: 从 PDF 文本提取实际章节起始页，并更新目录**

Run: `pdftotext tmp/rendered_report/report.pdf - | Select-String "项目成果与技术路线"`

Expected: 目录页码与章节实际起始页一致。

- [ ] **Step 4: 最终核验导出包**

```python
assert len(list(SCIENTIFIC_ASSET.glob("*.svg"))) == 7
assert len(list(SCIENTIFIC_ASSET.glob("*.pdf"))) == 7
assert len(list(SCIENTIFIC_ASSET.glob("*.png"))) == 7
```

## Self-review

- 需求覆盖：七张图、内容优化、全部表格样式、Word 回写和渲染核验均有对应任务。
- 占位检查：计划中不含 TBD/TODO 或无验证步骤的任务。
- 一致性：所有视觉输出由 Python/Matplotlib 完成；Word 仅嵌入 Python 导出的 PNG。
