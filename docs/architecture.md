# 技术架构

> 本文保留为早期技术说明。第一版合并审阅请优先阅读 [设计档案](v1/README.md) 中的 [软件架构框架图](v1/04-architecture.md)。

## 技术选择

- Kotlin + Jetpack Compose，单 Activity。
- `minSdk 26`、`targetSdk 35`，使用标准 Android APK。
- 系统 `TextToSpeech` 负责试听与音频合成。
- 系统 `PdfRenderer` 负责 PDF 原页渲染，PDFBox Android 负责文字与书签提取。
- 本地 JSON + 应用私有文件，不引入数据库与云端依赖。
- WorkManager 承担长时间音频合成。

这套选择避免 GMS 绑定，适用于普通 Android 设备，以及保留 Android 兼容应用层的鸿蒙设备。具体机型与系统版本仍需真机验收。

## 数据流

```text
系统文件选择器
      |
      v
BookRepository 复制原文件
      |
      +---- PDF ----> PDFBox: 页文字 / 行坐标 / 书签
      |
      +---- MD/TXT --> MarkdownParser: 章节 / 目录 / 纯正文
      |
      v
books/<id>/source.* + content.json
      |
      v
ReaderViewModel
      |
      +---- TextPaginator ------> Compose 横向分页与句/词高亮
      |
      +---- PdfRenderer --------> 原始 PDF 页面 + 文本行高亮
      |
      +---- ReaderTtsController -> 系统 TTS 播放
      |
      +---- AudioExportWorker --> 分段 WAV -> WavMerger -> 单个 WAV
```

## 主要模块

| 路径 | 职责 |
| --- | --- |
| `data/BookRepository.kt` | 导入、文件复制、书库与进度原子持久化 |
| `data/MarkdownParser.kt` | 多编码文本解码、章节和目录抽取 |
| `data/PdfBookParser.kt` | PDF 文字、书签与页面行坐标提取 |
| `data/SentenceTokenizer.kt` | TTS 句子切分及原文偏移映射 |
| `tts/ReaderTtsController.kt` | 系统 TTS 生命周期和词位置回调 |
| `ui/library/` | 本地书架与导入 |
| `ui/reader/` | 分页、PDF 页、目录、跳转、朗读控制 |
| `export/AudioExportWorker.kt` | 后台逐段合成、MediaStore 输出 |
| `export/WavMerger.kt` | 校验格式并流式合并 PCM WAV |

## 关键决定

### 文本分页

使用 Compose `TextMeasurer` 在实际可用宽度与行数下测量页面，避免按固定字符数切页造成溢出。目录仍以原章节为语义单位，翻页可以跨章节连续阅读。

### 高亮

句子分词会保留原始字符区间；Markdown/TXT 直接在 `AnnotatedString` 上添加背景色。系统 TTS 的 `onRangeStart` 提供当前词范围时再加强高亮。PDF 保存文本行在页面中的归一化坐标，绘制透明色块覆盖原页面。

PDF 坐标与旋转页面是最需要真机验证的部分。若某些 PDF 出现高亮偏移，应优先修复 `PdfBookParser` 的坐标归一化，而不是改显示层。

### 音频导出

系统 TTS 对单次文本长度和输出格式有引擎差异，因此按约 2800 字分块，逐段合成 WAV。所有片段必须具有相同 PCM 格式才允许合并；格式不一致时宁可明确失败，也不生成损坏文件。

Android 10 及以上写入 `Music/页声` 的 MediaStore；Android 8/9 写入应用专属外部音乐目录。后者能在旧系统上避免申请宽泛存储权限，但文件定位入口需要在真机阶段补充“打开导出文件”。

### 隐私

- 文件只在用户主动导入时复制到应用私有目录。
- 不使用网络权限。
- 不读取联系人、位置等无关数据。
- PDF 文本与音频合成均在本机完成。

## 已知风险

1. 不同厂商 TTS 引擎的 WAV 格式、语言包和中英文切换能力不一致。
2. Huawei/HarmonyOS 可能限制后台长任务，需要按机型验证 WorkManager。
3. PDFBox 行坐标在旋转页面、裁剪框和双栏排版上可能不准确。
4. 大型纯文本首次分页在 UI 线程测量，可能出现短暂卡顿；需在真机压测后引入分页缓存。
5. 当前没有 Android SDK 环境，代码尚未经过 Kotlin 编译器和 Android Lint。

## 后续架构演进

1. 引入 `Room` 存章节和分页缓存，支持超大书籍。
2. 将分页器移到可测试的布局服务，并为每章建立增量缓存。
3. 增加媒体播放器，实现已导出音频的重播与章节定位。
4. 为扫描 PDF 增加可插拔 OCR，优先离线方案。
5. 品牌确定后统一 applicationId、仓库名与包路径。
