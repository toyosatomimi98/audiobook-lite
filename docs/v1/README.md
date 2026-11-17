# 页声 PageVoice · 第一版设计档案

> - 工作版本：`v1-design-draft`
> - 文档日期：2026-11-17
> - 实现基线：`0.1.0-dev`
> - 产品名：页声（工作代号）
> - 验证状态：代码与设计基线已完成；编译、Lint、模拟器和真机验收 pending

## 这套文档回答什么

第一版文档不是功能清单的延长版，而是把产品意图、执行路径和系统边界放在同一套可追溯结构里。以后增加 OCR、云同步、音频书库或正式品牌时，应先在相应章节说明变化，再修改代码。

建议阅读顺序：

1. [设计 Teaser](01-design-teaser.md)：为什么做、给谁用、第一眼应该感受到什么。
2. [核心执行流水线](02-execution-pipeline.md)：文件如何变成可读页面、同步高亮和音频文件。
3. [流程图与状态机](03-flows.md)：用户怎么走、播放状态怎么变、失败在哪里分流。
4. [软件架构框架图](04-architecture.md)：各层职责、模块依赖、数据契约和演进边界。

## 一句话产品定义

**页声是一台优先服务眼睛的纸书阅读器；当你需要耳朵时，系统和文字会停在同一个句子上。**

## 第一版边界

### 包含

- 本地导入 PDF、Markdown 和 TXT。
- 纸书式横向分页、目录、快速跳转、阅读进度和排版设置。
- PDF 原页渲染、书签目录和文本行高亮。
- 系统 TTS 的句子播放、词位置跟随、跨章续读和语速控制。
- 整本书后台合成并合并为单个 WAV 文件。
- Android 8.0+ 标准 APK；不依赖 GMS。

### 不包含

- 账号、云同步、在线书城和 DRM。
- 扫描 PDF 的 OCR。
- 首版 EPUB、MOBI、DOCX。
- 已导出音频在应用内的完整播放器。
- 正式品牌命名与商店发布材料。

## 文档状态约定

| 标记 | 含义 |
| --- | --- |
| 设计完成 | 交互、数据契约和代码骨架已明确 |
| 静态通过 | 配置、XML、编码或链接检查通过 |
| 待构建 | 尚未经过 Kotlin/Android 编译 |
| 待设备 | 需要模拟器或真机验证，不能仅凭代码下结论 |

## 当前证据

| 项目 | 状态 | 证据 |
| --- | --- | --- |
| 书库与导入数据流 | 设计完成 | `BookRepository` |
| 文本分页与高亮 | 设计完成 | `TextPaginator`、`ReaderScreen` |
| PDF 原页与坐标 | 设计完成 / 待设备 | `PdfBookParser`、`PdfPageRenderer` |
| TTS 同步状态机 | 设计完成 / 待设备 | `ReaderViewModel`、`ReaderTtsController` |
| WAV 后台导出 | 设计完成 / 待设备 | `AudioExportWorker`、`WavMerger` |
| Windows / Ubuntu 构建入口 | 静态通过 | Gradle Wrapper、Make、Ubuntu Doctor、portability check |
| Ubuntu 24.04 CI | 配置完成 / 待远端运行 | `.github/workflows/android-ci.yml` |
| 静态配置 | 静态通过 | XML、TOML、UTF-8、包路径、Wrapper 结构 |
| 编译与 Lint | 待构建 | 当前电脑缺少 Android SDK、ADB、Gradle 缓存 |

## 文档维护规则

- 行为改变时同时更新本目录的对应流程和 [开发日志](../development-log.md)。
- Mermaid 图中的节点名应尽量对应真实模块名。
- 所有“兼容鸿蒙”结论必须附设备型号、系统版本和安装结果。
- 未测量过的性能数字只能写目标，不能写结果。
- 正式名称确定后，统一替换标题、包名、仓库名和安装标识。
