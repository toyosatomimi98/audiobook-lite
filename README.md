# 页声 · PageVoice

一个以“纸书阅读”为起点、逐步加入系统朗读与音频导出的 Android 应用。

> 工作代号暂定为“页声”，正式产品名和仓库名待定。旧仓库学习笔记暂时保留在根目录，后续可在确认后统一归档。

## 当前状态

这是 `0.1.0-dev` 初版工程，已经完成可直接继续开发的代码基线：

- 本地书库：从系统文件选择器导入 PDF、Markdown 或 TXT，并复制到应用私有目录。
- 阅读器：章节目录、横向翻页、快速跳转、阅读进度、字体大小和行距调整。
- 朗读：System TTS 句子级朗读、播放/暂停/前后句、语速调整、页码跟随。
- 跟随高亮：Markdown/TXT 显示句级与词级高亮；PDF 显示页内文本行高亮。
- 音频导出：通过系统 TTS 分块合成 WAV，并合并为单个文件。
- 兼容策略：标准 Android APK，`minSdk 26`，不依赖 Google Play 服务。

**尚未验证：** 当前电脑没有 Android SDK、ADB、模拟器或移动设备环境，因此首次编译、视觉回归和真机功能验收全部标为 pending。详见 [设备验收清单](docs/device-qa.md)。

## 快速开始

Windows 与 Ubuntu 24.04 使用同一套 Gradle、Kotlin 和 Android 工程。推荐使用 Android Studio Ladybug 或更新版本：

1. 安装 JDK 17、Android SDK 35 和 Android SDK Platform-Tools。
2. 项目已带 Gradle Wrapper；首次同步会下载 Gradle 8.11.1 与 Maven 依赖。
3. 连接 Android 8.0 以上设备，或启动 API 26/API 35 两个模拟器。
4. 先执行 `./gradlew testDebugUnitTest`，再执行 `./gradlew assembleDebug`。
5. 按 [设备验收清单](docs/device-qa.md) 完成 PDF、中文 Markdown、TTS 与导出测试。

Ubuntu 24.04 的完整步骤见 [Ubuntu 开发与调试指南](docs/ubuntu-development.md)。首次进入仓库建议执行：

```bash
chmod +x gradlew scripts/*.sh
bash scripts/ubuntu-doctor.sh
make check
make build
```

仓库内提供 [中文/English 验收样例](samples/灯塔与纸页.md)，覆盖标题目录、长短段落、引用与中英文朗读。PDF 请额外准备单栏、双栏和扫描版三份无版权测试文件。

## 文档

- [第一版设计档案](docs/v1/README.md) · 含设计 Teaser、执行流水线、流程图和软件架构框架图
- [产品范围与路线图](docs/product-roadmap.md)
- [技术架构](docs/architecture.md)
- [视觉与交互规范](docs/design-system.md)
- [开发日志](docs/development-log.md)
- [设备验收清单](docs/device-qa.md)
- [Ubuntu 24.04 开发与调试指南](docs/ubuntu-development.md)

## 仓库说明

原仓库中的 `misc.md`、`memo.md`、`useful_tips.md`、`fluentpython.py` 是历史学习笔记，当前没有删除。新应用代码集中在 `app/`、`docs/` 和 Gradle 配置中。
