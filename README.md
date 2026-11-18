# 页声 · PageVoice

把 PDF、Markdown 和 TXT 变成一本能读、能听、也能导出有声书的 Android 阅读器。页声是当前的工作名。

阅读翻页、目录和排版仍然放在第一位；需要时再打开朗读，让声音跟着文字走，而不是把页面变成播放器。

## 功能

- 导入 PDF、Markdown、TXT，保留本地书库和阅读进度。
- 横向翻页、章节目录、快速跳转、字号和行距调整。
- 女声、男声朗读预设，支持语速、前后句和跨章续读。
- 文本句级/词级高亮跟随，PDF 支持页内行高亮。
- 将整本书导出为一个 WAV 音频文件。
- 标准 Android APK，支持 Android 8.0+，不依赖 Google Play 服务。

## 开始开发

需要 Android Studio、JDK 17/21、Android SDK 35 和 Platform-Tools。

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Ubuntu 24.04 先运行环境检查：

```bash
bash scripts/ubuntu-doctor.sh
make check
make build
```

完整的 Ubuntu 配置见 [开发指南](docs/ubuntu-development.md)。可以先用 [示例文稿](samples/灯塔与纸页.md) 验证阅读和朗读流程。

## 当前进度

当前版本是 `0.1.0-dev`。应用、设计文档和构建脚手架已经就位，下一步是首次 Android 构建和真机验收，具体项目见 [设备验收清单](docs/device-qa.md)。

## 文档

- [第一版设计档案](docs/v1/README.md)：产品叙事、执行流水线、流程图和架构图。
- [开发日志](docs/development-log.md)：关键决策、实现记录和待验证问题。
- [Ubuntu 24.04 开发指南](docs/ubuntu-development.md)：环境、SDK、模拟器和真机调试。

## 仓库

`misc.md`、`memo.md`、`useful_tips.md` 和 `fluentpython.py` 是仓库原有的学习笔记，暂时保留。应用代码在 `app/`，文档在 `docs/`。
