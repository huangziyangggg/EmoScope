# EmoScope 结题报告初稿 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `item` 中交付一份符合学校模板逻辑、以中期报告为演进起点的 EmoScope 项目结题报告 Word 初稿。

**Architecture:** 报告以“中期设想—实际难点—调整措施—最终实现”为贯穿叙事；代码和项目说明用于核实终期成果。使用 Python-docx 生成可编辑 DOCX，保留意见与签名栏，再渲染为页面图像进行版式检查。

**Tech Stack:** Python 3、python-docx、LibreOffice 渲染器、PowerShell。

## Global Constraints

- 正文结构包含课题背景、研究内容与过程、研究方法、项目创新点、项目成果、项目总结六部分，正文不少于 3000 字。
- 中期报告仅作为原始路线和阶段依据；终期事实须由当前代码、README 或已有材料支持。
- 产品定位为情绪记录、心理健康辅助与自我调节支持，不作医疗诊断或临床效果承诺。
- 输出为 `item/EmoScope项目结题报告（初稿）.docx`，意见、签名和日期栏保持可编辑。

---

### Task 1: 提取并核实报告事实

**Files:**
- Read: `item/中期报告(1).docx`
- Read: `README.zh-CN.md`
- Read: `app/src/main/java/com/example/emoscope/`
- Create: `tmp/final-report-facts.md`

**Interfaces:**
- Consumes: 中期报告中的原始目标、团队与经费信息；README 和源码中的最终功能事实。
- Produces: 用于写作的“中期—问题—调整—终期成果”事实清单。

- [ ] **Step 1: 提取中期报告的章节、团队信息和技术设想。**

  Run: `python -c "from docx import Document; d=Document(r'item/中期报告(1).docx'); print(len(d.paragraphs))"`

  Expected: 命令成功，并能读取中期报告段落。

- [ ] **Step 2: 核实最终功能。**

  Run: `rg -n "FaceLandmarker|SpeechRecognizer|SQLite|DeepSeek|Biometric|AlarmManager" README.zh-CN.md app/src/main/java`

  Expected: 输出当前实现中与面部分析、语音、数据存储、AI、隐私和提醒有关的证据。

- [ ] **Step 3: 编写事实清单。**

  Write `tmp/final-report-facts.md`，按“中期设想 / 实际困难 / 解决或调整 / 最终成果 / 证据来源”五列记录，禁止将计划性描述作为完成事实。

- [ ] **Step 4: 核对事实边界。**

  Run: `rg -n "临床|诊断|治愈|医疗级" tmp/final-report-facts.md`

  Expected: 不包含医疗诊断和效果承诺。

### Task 2: 生成结题报告 DOCX 初稿

**Files:**
- Read: `tmp/final-report-facts.md`
- Read: `item/附件1：哈尔滨工业大学大一年度项目结题报告.doc`
- Create: `tmp/build_final_report.py`
- Create: `item/EmoScope项目结题报告（初稿）.docx`

**Interfaces:**
- Consumes: 任务 1 的事实清单和结题模板要求。
- Produces: 包含封面字段、团队表格、中英文摘要、六部分正文、参考文献和意见栏的可编辑 Word 报告。

- [ ] **Step 1: 编写报告正文。**

  在构建脚本中写入六部分正文，每个关键模块采用“中期设想—实际问题—调整措施—最终实现”结构；为中英文摘要、关键词、教师意见、专家意见和签名日期建立独立段落或表格单元格。

- [ ] **Step 2: 设置版式。**

  在构建脚本中设置 A4 页面、2 cm 左右页边距、宋体正文和黑体标题；以 Word 标题样式设置章、节层级；用表格实现成员与意见栏。

- [ ] **Step 3: 执行构建。**

  Run: `python tmp/build_final_report.py`

  Expected: 生成 `item/EmoScope项目结题报告（初稿）.docx`，文件非空且可由 python-docx 读取。

- [ ] **Step 4: 内容检查。**

  Run: `python -c "from docx import Document; d=Document(r'item/EmoScope项目结题报告（初稿）.docx'); s=''.join(p.text for p in d.paragraphs); print(len(s)); assert len(s) > 3000"`

  Expected: 输出大于 3000 的字符数且断言通过。

### Task 3: 渲染和交付检查

**Files:**
- Read: `item/EmoScope项目结题报告（初稿）.docx`
- Create: `tmp/final-report-render/`

**Interfaces:**
- Consumes: 任务 2 的 DOCX。
- Produces: 已通过文本、表格、分页和中文渲染检查的最终初稿。

- [ ] **Step 1: 渲染 DOCX。**

  Run: `python C:/Users/Ziyang Huang/.codex/plugins/cache/openai-primary-runtime/documents/26.619.11828/skills/documents/render_docx.py "item/EmoScope项目结题报告（初稿）.docx" --output_dir tmp/final-report-render --emit_pdf`

  Expected: 生成 `page-*.png` 与 PDF。

- [ ] **Step 2: 检查每页图像。**

  Inspect: `tmp/final-report-render/page-*.png`

  Expected: 中文无缺字；表格不截断；标题层级、页边距和分页自然；意见栏可填写。

- [ ] **Step 3: 修复并复检。**

  如发现截断、空白过大、文字重叠或页眉页脚错位，调整 `tmp/build_final_report.py` 后重新执行任务 2、步骤 3 至任务 3、步骤 2。

- [ ] **Step 4: 确认交付。**

  Run: `Get-Item -LiteralPath 'item/EmoScope项目结题报告（初稿）.docx' | Select-Object Name,Length,LastWriteTime`

  Expected: 输出最终 DOCX 的名称、非零大小和生成时间。
