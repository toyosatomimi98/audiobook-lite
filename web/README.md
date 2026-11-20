# PageVoice Web

页声 Web 端的第一版阅读界面。它与 Android 共用阅读模型，并通过 `shared/` 中的协议同步进度。

## 当前能力

- Markdown、TXT 导入和本地书架。
- 响应式手机/电脑布局。
- 章节阅读、滚动位置和百分比进度。
- 浏览器 Web Speech 朗读，支持女声/男声和语速。
- 自动保存本地进度。
- 配置个人同步服务后，按书本指纹合并手机与其他电脑的进度。
- PWA 应用外壳；HTTPS 或 localhost 下可离线打开。

PDF 原页渲染暂时只保留在 Android 端。

## 运行

```bash
node sync-server/src/server.mjs
```

打开 `http://127.0.0.1:8787`。服务会显示个人同步令牌，Web 端的“同步设置”中填入同一令牌。

单独做静态开发时可以运行任意静态文件服务器，但跨域的 `/api/v1` 地址需要在同步设置中手工配置。生产环境和手机局域网访问请阅读 [同步框架](../docs/sync-framework.md)。

## 测试

```bash
node --test web/test/hash.test.mjs
```

书籍指纹同时验证了浏览器 Web Crypto 路径和纯 JavaScript SHA-256 降级路径。
