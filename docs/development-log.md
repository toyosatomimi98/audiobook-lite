# 开发日志

本文件按时间倒序记录有追溯价值的决策、实现与验证。每次涉及行为变化，都应补充条目，而不是只依赖 Git commit message。

## 2026-11-17 · M0 代码基线

### Ubuntu 24.04 开发连续性

- POSIX Gradle Wrapper 增加 `JAVA_HOME` 检测和明确的缺 Java 错误。
- 增加 Ubuntu 环境诊断脚本，覆盖 Java、SDK 35、ADB、local.properties、KVM、udev、内存和磁盘。
- 增加机器本地 SDK 路径配置脚本，不把 `local.properties` 提交进仓库。
- 增加跨平台 `Makefile`，命令通过 `sh ./gradlew` 运行，不依赖 Git 是否带回执行位。
- 增加 shell 语法、CRLF、Wrapper 与 CI 文件的可移植性检查。
- 增加 `ubuntu-24.04` GitHub Actions：单测、Lint、Debug APK 与 artifact 上传。
- 增加 `.editorconfig`，补齐 `.gitattributes` 中脚本、CI、PNG 的处理规则。
- 增加 Ubuntu 原生开发、真机、模拟器、WSL 边界和常见问题文档。

### 第一版设计文档

- 新增 `docs/v1/` 设计档案入口。
- 完成产品设计 Teaser、首屏与阅读构图、视觉承诺和成功标准。
- 完成导入、分页、PDF、TTS 高亮、音频导出的执行流水线说明。
- 完成用户主流程、阅读 UI 状态机、播放状态机、TTS 与导出时序图。
- 完成分层软件架构、模块职责、数据模型、持久化、线程模型和扩展接缝框架图。
- 新增 SVG 设计海报，并用 Edge 无头渲染 PNG 预览完成视觉检查。
- 通过 Markdown 相对链接和 SVG XML 静态校验。

### 上下文

用户希望把长期闲置的 `practice` 仓库改造成一个 Android 有声书应用。核心要求是先从纸书阅读器做起，再叠加 PDF/Markdown 导入、系统朗读、文字跟随高亮和音频文件导出。当前电脑没有移动调试环境，明确不在本机做模拟器/真机调试。

### 仓库盘点

- 远端仓库只有 `README.md`、`misc.md`、`memo.md`、`useful_tips.md` 和 `fluentpython.py`。
- 内容是英语学习/日常笔记，没有 Android 或前端工程可复用。
- 历史文件暂时保留，避免在未确认用途前删除用户资料。

### 完成的代码

- 建立 Kotlin、Jetpack Compose、Android Gradle Plugin 工程配置。
- 本地书库和原子 JSON 持久化。
- PDF 页面文字、行坐标和目录书签提取。
- Markdown/TXT 多编码解析与目录生成。
- 暖纸色书库与阅读器界面。
- 按实际布局宽度/行数测量的文本分页。
- 目录、百分比跳转、字号与行距调整。
- 系统 TTS 句子队列、前后句、语速和跨章续读。
- Markdown 句/词高亮，PDF 文本行高亮。
- WorkManager 整本 WAV 合成、PCM 校验与流式合并。
- 核心解析和 WAV 合并的 JUnit 单测。

### 技术决策

- 使用标准 Android APK，不依赖 GMS，以提高普通 Android 与兼容鸿蒙设备覆盖。
- 首版不把系统 TTS 换成分段波形播放器，而是直接调用系统朗读能力。
- 音频导出一律生成 WAV，因为 Android 系统 TTS 原生文件输出通常为 WAV；MP3 转码留待后续评估。
- PDF 高亮采用文本行矩形，不承诺字级精确边界。

### 已做的静态检查

- 检查仓库原文件与 Git 状态。
- 检查 Java、Gradle、Android SDK、ADB 是否可用。
- 结果：JDK 21 可用；Gradle、Android SDK、ADB 不可用。
- 校验全部 Android XML、版本目录 TOML、Kotlin 包路径与文本 UTF-8 编码。
- 补入并检查 Gradle 8.11.1 官方 Wrapper JAR 的 ZIP 结构。
- 因此没有执行 Kotlin 编译、单测、Lint、APK 构建或设备运行。

### 待验证与风险

- 已补入 Gradle 8.11.1 官方 Wrapper JAR；但没有 Gradle 依赖缓存，首次同步仍需联网。
- Compose API 与 PDFBox API 需要首次编译校正。
- PDF 高亮坐标、TTS 词位置、MediaStore 输出和后台任务必须真机验证。
- 正式名称、包名与仓库名仍未确定。

### 下一轮建议

1. 在高配置电脑完成首次编译，记录所有 API 差异。
2. 用样例 Markdown 与三类 PDF（单栏、双栏、扫描）打通验收。
3. 根据真实 TTS 引擎输出决定是否保留 WAV 合并逻辑。
4. 做第一轮视觉截图评审，再决定是否增加深色主题。
