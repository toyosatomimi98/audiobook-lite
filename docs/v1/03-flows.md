# 流程图、用户路径与状态机

本文聚焦“谁在什么状态下做了什么”。软件分层见 [软件架构框架图](04-architecture.md)，实现阶段细节见 [核心执行流水线](02-execution-pipeline.md)。

## 1. 用户主流程

```mermaid
flowchart TD
    Launch[启动应用] --> Loading[恢复本地书库]
    Loading --> Empty{书架为空?}
    Empty -->|是| Welcome[空书架与导入入口]
    Empty -->|否| Shelf[最近阅读书架]
    Welcome --> Pick[系统文件选择器]
    Shelf --> Pick
    Shelf --> Search[搜索书名 / 作者]
    Search --> Open[打开书籍]
    Pick --> Cancel{取消选择?}
    Cancel -->|是| Previous[保持原页面]
    Cancel -->|否| Importing[后台复制与解析]
    Importing --> Error{导入成功?}
    Error -->|否| Message[中文错误提示]
    Error -->|是| Shelf
    Open --> Restore[恢复章节 / 字符偏移]
    Restore --> Reader[阅读页]
```

## 2. 阅读流程

```mermaid
flowchart LR
    Reader[阅读页] --> Swipe[左右翻页]
    Reader --> Toc[打开目录]
    Reader --> Jump[百分比跳转]
    Reader --> Type[调整字号 / 行距]
    Reader --> Play[开始朗读]
    Reader --> Export[导出音频]

    Swipe --> Progress[保存进度]
    Toc --> Progress
    Jump --> Progress
    Type --> Repaginate[重新分页]
    Repaginate --> Restore[按字符偏移恢复位置]
    Restore --> Progress
```

### 文本页点击规则

- 点击正文：从点击位置所在句开始朗读。
- 点击页边空白：显示或隐藏工具栏。
- 左右滑动：翻页。
- 工具栏隐藏且未播放：保留右下角耳机入口。

## 3. 阅读器 UI 状态

```mermaid
stateDiagram-v2
    [*] --> Loading
    Loading --> Reading: 正文加载成功
    Loading --> Failed: 正文 / 元数据失败
    Reading --> ChromeVisible: 打开书架
    Reading --> ChromeHidden: 点空白
    ChromeHidden --> ChromeVisible: 点空白 / 开始播放
    ChromeVisible --> Reading: 关闭菜单
    Reading --> TocOpen: 点击目录
    Reading --> JumpOpen: 点击快速跳转
    Reading --> TypeOpen: 点击排版
    TocOpen --> Reading: 选择章节 / 关闭
    JumpOpen --> Reading: 跳转 / 关闭
    TypeOpen --> Reading: 关闭
    Reading --> [*]: 返回书架
    Failed --> [*]: 返回书架
```

## 4. 播放状态机

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Speaking: 点击播放 / 点击正文
    Speaking --> Paused: 点击暂停
    Paused --> Speaking: 点击播放
    Speaking --> Following: onStart
    Following --> Following: onRangeStart
    Following --> Advancing: onDone
    Advancing --> Following: 下一句
    Advancing --> ChapterChange: 章节结束
    ChapterChange --> Following: 下一有字章节
    ChapterChange --> Completed: 全书结束
    Following --> Paused: 点击暂停
    Following --> Seeking: 目录 / 跳转 / 点击新句
    Seeking --> Following: 新句开始
    Following --> Failed: TTS 错误
    Speaking --> Failed: 初始化 / 语言包失败
    Completed --> Idle: 用户再次播放
    Failed --> Idle: 用户重试
```

## 5. TTS 同步时序

```mermaid
sequenceDiagram
    actor U as 用户
    participant UI as ReaderScreen
    participant VM as ReaderViewModel
    participant TK as SentenceTokenizer
    participant TTS as System TTS
    participant HL as Highlight Layer

    U->>UI: 点击正文位置
    UI->>VM: startPlaybackAt(charOffset)
    VM->>TK: 定位所在 SentenceSpan
    TK-->>VM: start / end / text
    VM->>VM: 更新 activeSentence 与播放索引
    VM->>TTS: speak(text, utteranceId)
    TTS-->>VM: onStart(utteranceId)
    VM-->>HL: 句级高亮
    TTS-->>VM: onRangeStart(start, end)
    VM->>VM: 换算为章节绝对偏移
    VM-->>HL: 当前词强化高亮
    UI->>UI: 当前词跨页时滚动到目标页
    TTS-->>VM: onDone(utteranceId)
    VM->>VM: 索引 + 1 或进入下一章
```

### 为什么 utteranceId 必须唯一且可验证

播放、暂停、跳转可能连续发生时，旧引擎回调仍可能晚到。`utteranceId` 内含章节与句子索引；接收回调前先校验当前期望值，避免旧句子把新位置的高亮或队列改坏。

## 6. 音频导出时序

```mermaid
sequenceDiagram
    actor U as 用户
    participant UI as ReaderScreen
    participant VM as ReaderViewModel
    participant WM as WorkManager
    participant W as AudioExportWorker
    participant TTS as System TTS
    participant M as WavMerger
    participant OS as MediaStore

    U->>UI: 导出整本音频
    UI->>UI: Android 13+ 请求通知权限
    UI->>VM: startAudioExport()
    VM->>WM: enqueueUniqueWork(KEEP)
    WM->>W: doWork(bookId)
    W->>W: 读取正文并分块
    loop 每个文本块
        W->>TTS: synthesizeToFile()
        TTS-->>W: WAV 文件
        W->>W: 校验 WAV
        W->>WM: setForeground(progress)
    end
    W->>M: 合并 PCM 数据
    M-->>W: 总时长与格式
    W->>OS: 写入 Music/页声
    OS-->>W: 完成输出
    W-->>WM: Result.success(path, duration)
    WM-->>VM: WorkInfo SUCCEEDED
    VM-->>UI: 显示导出位置与时长
```

## 7. 数据流

```mermaid
flowchart LR
    SAF[系统 URI] -->|读取一次| Private[应用私有原文件]
    Private --> Parser[格式解析]
    Parser --> Content[BookContent]
    Content --> Metadata[LibraryBook]
    Metadata --> LibraryJSON[library.json]
    Content --> ContentJSON[content.json]

    Content --> ReaderState[ReaderUiState]
    ReaderState --> UI[Compose UI]
    ReaderState --> TTS[System TTS]
    TTS --> ReaderState
    ReaderState --> Progress[ReadingProgress]
    Progress --> LibraryJSON

    Content --> Exporter[AudioExportWorker]
    Exporter --> AudioFile[单个 WAV]
```

## 8. 失败路径

```mermaid
flowchart TD
    Failure[发生错误] --> Where{错误来源}
    Where -->|导入| ImportUI[Snackbar / 清理临时目录]
    Where -->|PDF 文字| PdfDegrade[保留页面, 标记朗读受限]
    Where -->|TTS 初始化| TtsMessage[阅读继续, 提示系统语音设置]
    Where -->|TTS 回调| PlaybackStop[停止播放, 保留当前页]
    Where -->|导出通知单段| ExportFail[WorkInfo FAILED + 中文原因]
    Where -->|WAV 格式不一致| MergeFail[删除半成品, 明确要求统一语音包]
    Where -->|书库正文缺失| ReaderFail[无法打开页 + 返回书架]
```

## 9. 设备验收必须覆盖的状态转换

1. 阅读中播放，用户连滑三页，再等待自动续句。
2. 播放中打开目录并跳到另一章。
3. 播放中锁屏、切后台、返回应用。
4. 暂停后滑页，再点击播放。
5. 系统 TTS 不支持词位置时验证句级降级。
6. 导出时切后台，完成通知与 WorkInfo 一致。
7. 导出中退出阅读页，再重新打开同一本书。
8. 扫描 PDF 无法朗读时仍能翻页与跳转。
