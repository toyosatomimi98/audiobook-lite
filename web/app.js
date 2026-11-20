import { sha256, toHex } from "./lib/hash.js";

const BOOKS_KEY = "pagevoice.web.books.v1";
const META_KEY = "pagevoice.web.meta.v1";
const SCHEMA_VERSION = 1;

const elements = {
  appShell: document.querySelector("#appShell"),
  bookList: document.querySelector("#bookList"),
  bookCount: document.querySelector("#bookCount"),
  syncBookCount: document.querySelector("#syncBookCount"),
  searchInput: document.querySelector("#searchInput"),
  fileInput: document.querySelector("#fileInput"),
  readerEmpty: document.querySelector("#readerEmpty"),
  readerView: document.querySelector("#readerView"),
  bookFormat: document.querySelector("#bookFormat"),
  bookTitle: document.querySelector("#bookTitle"),
  chapterIndex: document.querySelector("#chapterIndex"),
  chapterTitle: document.querySelector("#chapterTitle"),
  pageContent: document.querySelector("#pageContent"),
  readingProgressBar: document.querySelector("#readingProgressBar"),
  previousChapterButton: document.querySelector("#previousChapterButton"),
  nextChapterButton: document.querySelector("#nextChapterButton"),
  previousSentenceButton: document.querySelector("#previousSentenceButton"),
  nextSentenceButton: document.querySelector("#nextSentenceButton"),
  playButton: document.querySelector("#playButton"),
  voicePreset: document.querySelector("#voicePreset"),
  speedButton: document.querySelector("#speedButton"),
  fontButton: document.querySelector("#fontButton"),
  nowReadingLabel: document.querySelector("#nowReadingLabel"),
  voiceDot: document.querySelector("#voiceDot"),
  syncState: document.querySelector("#syncState"),
  syncBadge: document.querySelector("#syncBadge"),
  syncButton: document.querySelector("#syncButton"),
  mobileBackButton: document.querySelector("#mobileBackButton"),
  settingsDialog: document.querySelector("#settingsDialog"),
  openSettingsButton: document.querySelector("#openSettingsButton"),
  syncEndpointInput: document.querySelector("#syncEndpointInput"),
  syncTokenInput: document.querySelector("#syncTokenInput"),
  deviceIdInput: document.querySelector("#deviceIdInput"),
  resetDeviceButton: document.querySelector("#resetDeviceButton"),
  saveSettingsButton: document.querySelector("#saveSettingsButton"),
  toast: document.querySelector("#toast"),
};

const app = {
  books: {},
  settings: {
    syncEndpoint: defaultEndpoint(),
    token: "",
    deviceId: newDeviceId(),
    voicePreset: "female",
    speechRate: 1,
    fontScale: 0,
  },
  remoteProgress: {},
  activeBookKey: null,
  activeChapter: 0,
  currentSentences: [],
  currentSentenceIndex: -1,
  activeWordStart: -1,
  activeWordEnd: -1,
  voices: [],
  speechSession: 0,
  progressTimer: 0,
  progressSaveTimer: 0,
  shelfRenderTimer: 0,
  wordRenderFrame: 0,
  syncTimer: 0,
  syncInFlight: false,
  toastTimer: 0,
  lastScrolledSentence: -1,
};

const speedValues = [0.75, 0.9, 1, 1.15, 1.35, 1.5];
const fontScales = [0, 1, 2];

void bootstrap().catch((error) => {
  console.error(error);
  toast(`Web 初始化失败：${error.message}`);
});

async function bootstrap() {
  loadState();
  bindEvents();
  loadVoices();
  if (Object.keys(app.books).length === 0) {
    await addSampleBook();
  }
  renderShelf();
  updateSyncState();
  const initial = app.settings.lastBookKey && app.books[app.settings.lastBookKey]
    ? app.settings.lastBookKey
    : Object.keys(app.books).sort((left, right) =>
        compareBooks(app.books[left], app.books[right])
      )[0];
  if (initial) openBook(initial, { focusReader: false });
  registerServiceWorker();
}

function bindEvents() {
  elements.searchInput.addEventListener("input", renderShelf);
  elements.fileInput.addEventListener("change", importFiles);
  elements.syncButton.addEventListener("click", () => syncProgress());
  elements.openSettingsButton.addEventListener("click", openSettings);
  elements.saveSettingsButton.addEventListener("click", saveSettingsAndSync);
  elements.resetDeviceButton.addEventListener("click", resetDeviceId);
  elements.mobileBackButton.addEventListener("click", showLibrary);
  elements.previousChapterButton.addEventListener("click", () => changeChapter(-1));
  elements.nextChapterButton.addEventListener("click", () => changeChapter(1));
  elements.previousSentenceButton.addEventListener("click", previousSentence);
  elements.nextSentenceButton.addEventListener("click", nextSentence);
  elements.playButton.addEventListener("click", toggleSpeech);
  elements.voicePreset.addEventListener("change", () => {
    app.settings.voicePreset = elements.voicePreset.value;
    persistMeta();
    if (app.currentSentenceIndex >= 0) startSentence(app.currentSentenceIndex);
  });
  elements.speedButton.addEventListener("click", cycleSpeed);
  elements.fontButton.addEventListener("click", cycleFontScale);
  elements.pageContent.addEventListener("click", (event) => {
    const sentence = event.target.closest(".sentence");
    if (!sentence) return;
    startSentence(Number(sentence.dataset.index));
  });
  window.addEventListener("beforeunload", () => {
    speechSynthesis.cancel();
    flushMeta();
  });
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") {
      speechSynthesis.cancel();
      flushMeta();
      scheduleProgressSync(0);
    }
  });
  if ("speechSynthesis" in window) {
    speechSynthesis.addEventListener("voiceschanged", loadVoices);
  }
}

function loadState() {
  const library = safeParse(localStorage.getItem(BOOKS_KEY));
  const meta = safeParse(localStorage.getItem(META_KEY));
  if (library?.schemaVersion === SCHEMA_VERSION && library.books) {
    app.books = library.books;
  }
  if (meta?.schemaVersion === SCHEMA_VERSION) {
    app.settings = { ...app.settings, ...(meta.settings ?? {}) };
    app.remoteProgress = meta.remoteProgress ?? {};
    app.settings.lastBookKey = meta.lastBookKey ?? null;
    for (const [bookKey, progress] of Object.entries(meta.progress ?? {})) {
      if (app.books[bookKey]) app.books[bookKey].progress = normalizeProgress(progress);
    }
  }
  if (!app.settings.deviceId) app.settings.deviceId = newDeviceId();
  elements.voicePreset.value = app.settings.voicePreset;
  applyFontScale();
  updateSpeedLabel();
}

function persistLibrary() {
  try {
    localStorage.setItem(BOOKS_KEY, JSON.stringify({
      schemaVersion: SCHEMA_VERSION,
      books: app.books,
    }));
  } catch {
    toast("书架内容较大，浏览器本地存储空间不足。");
  }
}

function persistMeta() {
  const progress = {};
  for (const [bookKey, book] of Object.entries(app.books)) {
    progress[bookKey] = book.progress;
  }
  try {
    localStorage.setItem(META_KEY, JSON.stringify({
      schemaVersion: SCHEMA_VERSION,
      settings: app.settings,
      progress,
      remoteProgress: app.remoteProgress,
      lastBookKey: app.activeBookKey ?? app.settings.lastBookKey ?? null,
    }));
  } catch {
    toast("阅读进度保存失败，请检查浏览器存储权限。");
  }
}

function flushMeta() {
  clearTimeout(app.progressSaveTimer);
  persistMeta();
}

async function importFiles(event) {
  const files = [...event.target.files];
  event.target.value = "";
  for (const file of files) {
    if (!/\.(md|markdown|txt)$/i.test(file.name)) {
      toast("Web 初版支持 Markdown 和 TXT；PDF 请继续使用 Android 端。");
      continue;
    }
    try {
      const bytes = new Uint8Array(await file.arrayBuffer());
      const bookKey = toHex(await sha256(bytes));
      const text = decodeText(bytes);
      const title = file.name.replace(/\.(md|markdown|txt)$/i, "");
      const chapters = parseMarkdown(text, title);
      const previous = app.books[bookKey] ?? app.remoteProgress[bookKey];
      const progress = previous?.progress ?? previous ?? defaultProgress();
      app.books[bookKey] = {
        bookKey,
        title,
        format: /\.pdf$/i.test(file.name) ? "pdf" : "text",
        chapters,
        importedAt: app.books[bookKey]?.importedAt ?? Date.now(),
        lastOpenedAt: Date.now(),
        progress: normalizeProgress(progress),
      };
      delete app.remoteProgress[bookKey];
      persistLibrary();
      persistMeta();
      renderShelf();
      openBook(bookKey);
      toast(`《${title}》已加入书架。`);
    } catch (error) {
      console.error(error);
      toast(`导入失败：${error.message}`);
    }
  }
}

function parseMarkdown(source, fallbackTitle) {
  const normalized = source.replace(/\r\n?/g, "\n");
  const blocks = [];
  let title = "前言";
  let depth = 0;
  let body = [];

  const flush = () => {
    const text = cleanBody(body.join("\n")).trim();
    if (text) blocks.push({ title: title || fallbackTitle, depth, text });
    body = [];
  };

  for (const line of normalized.split("\n")) {
    const heading = line.trim().match(/^(#{1,6})\s+(.+?)\s*#*$/);
    if (heading) {
      flush();
      depth = heading[1].length - 1;
      title = cleanInline(heading[2]);
    } else {
      body.push(line);
    }
  }
  flush();

  if (blocks.length === 0) {
    const text = cleanBody(normalized).trim();
    if (!text) throw new Error("文件里没有可阅读的文字。");
    blocks.push({ title: fallbackTitle, depth: 0, text });
  }
  return blocks;
}

function cleanBody(body) {
  let codeFence = false;
  return body.split("\n").map((line) => {
    const trimmed = line.trimStart();
    if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
      codeFence = !codeFence;
      return "";
    }
    if (codeFence) return line;
    if (trimmed.startsWith(">")) return `  ${cleanInline(trimmed.slice(1).trim())}`;
    if (/^[-*+]\s+/.test(trimmed)) return `• ${cleanInline(trimmed.slice(2))}`;
    if (/^\d+[.)]\s+/.test(trimmed)) return cleanInline(trimmed);
    return cleanInline(line);
  }).join("\n").replace(/\n{3,}/g, "\n\n");
}

function cleanInline(value) {
  return value
    .replace(/!\[([^\]]*)]\([^)]*\)/g, "$1")
    .replace(/\[([^\]]+)]\([^)]*\)/g, "$1")
    .replace(/<https?:\/\/[^>]+>/g, "")
    .replace(/<[^>]+>/g, "")
    .replace(/(\*\*|__|~~|`|\*)/g, "")
    .trimEnd();
}

function renderShelf() {
  const query = elements.searchInput.value.trim().toLocaleLowerCase();
  const books = Object.values(app.books)
    .filter((book) => !query || book.title.toLocaleLowerCase().includes(query))
    .sort(compareBooks);
  elements.bookList.replaceChildren();

  for (const book of books) {
    const progress = normalizeProgress(book.progress);
    const button = node("button", "book-card");
    button.type = "button";
    button.setAttribute("aria-current", String(app.activeBookKey === book.bookKey));
    button.addEventListener("click", () => openBook(book.bookKey));

    const spine = node("span", "book-spine", "页声");
    const info = node("span", "book-info");
    info.append(
      node("strong", "", book.title),
      node("span", "", `${book.chapters.length} 章 · ${Math.round(progress.percent * 100)}%`),
    );
    const percent = node(
      "span",
      "book-progress",
      progress.clientUpdatedAt ? `${Math.round(progress.percent * 100)}%` : "NEW",
    );
    button.append(spine, info, percent);
    elements.bookList.append(button);
  }

  elements.bookCount.textContent = `${Object.keys(app.books).length} 本书`;
  const remoteCount = Object.keys(app.remoteProgress).filter(
    (bookKey) => !app.books[bookKey],
  ).length;
  elements.syncBookCount.textContent = remoteCount ? `${remoteCount} 条待导入进度` : "";
}

function openBook(bookKey, { focusReader = true } = {}) {
  const book = app.books[bookKey];
  if (!book) return;
  stopSpeech({ render: false });
  app.activeBookKey = bookKey;
  app.activeChapter = Math.min(
    normalizeProgress(book.progress).chapterIndex,
    Math.max(book.chapters.length - 1, 0),
  );
  app.currentSentenceIndex = -1;
  app.activeWordStart = -1;
  app.activeWordEnd = -1;
  app.lastScrolledSentence = -1;
  book.lastOpenedAt = Date.now();

  elements.readerEmpty.hidden = true;
  elements.readerView.hidden = false;
  elements.appShell.classList.add("is-reading");
  elements.bookTitle.textContent = book.title;
  elements.bookFormat.textContent = `${book.format.toUpperCase()} · ${book.chapters.length} CHAPTERS`;
  document.title = `${book.title} · 页声`;
  renderChapter();
  renderShelf();
  persistMeta();
  if (focusReader && window.innerWidth <= 900) elements.readerView.scrollIntoView();
}

function showLibrary() {
  stopSpeech();
  elements.appShell.classList.remove("is-reading");
  elements.settingsDialog.close();
}

function renderChapter() {
  const book = activeBook();
  if (!book) return;
  const chapter = book.chapters[app.activeChapter];
  if (!chapter) return;

  app.currentSentences = tokenizeSentences(chapter.text);
  if (app.currentSentenceIndex >= app.currentSentences.length) {
    app.currentSentenceIndex = -1;
  }
  elements.chapterIndex.textContent = `第 ${app.activeChapter + 1} / ${book.chapters.length} 章`;
  elements.chapterTitle.textContent = chapter.title;
  elements.previousChapterButton.disabled = app.activeChapter <= 0;
  elements.nextChapterButton.disabled = app.activeChapter >= book.chapters.length - 1;

  elements.pageContent.replaceChildren();
  let previousEnd = 0;
  app.currentSentences.forEach((sentence, index) => {
    if (sentence.start > previousEnd) {
      elements.pageContent.append(document.createTextNode(chapter.text.slice(previousEnd, sentence.start)));
    }
    const span = node("span", "sentence");
    span.dataset.index = String(index);
    span.dataset.start = String(sentence.start);
    if (index === app.currentSentenceIndex) span.classList.add("is-speaking");
    appendSentenceContent(span, sentence, index === app.currentSentenceIndex);
    elements.pageContent.append(span);
    previousEnd = sentence.end;
  });
  if (previousEnd < chapter.text.length) {
    elements.pageContent.append(document.createTextNode(chapter.text.slice(previousEnd)));
  }

  updateProgressChrome();
  updateSpeechChrome();
  if (app.currentSentenceIndex >= 0 && app.lastScrolledSentence !== app.currentSentenceIndex) {
    app.lastScrolledSentence = app.currentSentenceIndex;
    requestAnimationFrame(() => {
      elements.pageContent.querySelector(".sentence.is-speaking")
        ?.scrollIntoView({ block: "center", behavior: "smooth" });
    });
  }
}

function appendSentenceContent(container, sentence, active) {
  if (!active || app.activeWordStart < 0 || app.activeWordEnd <= app.activeWordStart) {
    container.textContent = sentence.text;
    return;
  }
  const wordStart = Math.max(sentence.start, app.activeWordStart);
  const wordEnd = Math.min(sentence.end, app.activeWordEnd);
  if (wordStart >= wordEnd) {
    container.textContent = sentence.text;
    return;
  }
  container.append(
    document.createTextNode(sentence.text.slice(0, wordStart - sentence.start)),
    node(
      "span",
      "spoken-word",
      sentence.text.slice(wordStart - sentence.start, wordEnd - sentence.start),
    ),
    document.createTextNode(sentence.text.slice(wordEnd - sentence.start)),
  );
}

function changeChapter(delta) {
  const book = activeBook();
  if (!book) return;
  const next = app.activeChapter + delta;
  if (next < 0 || next >= book.chapters.length) return;
  stopSpeech({ render: false });
  app.activeChapter = next;
  app.currentSentenceIndex = -1;
  app.activeWordStart = -1;
  app.activeWordEnd = -1;
  app.lastScrolledSentence = -1;
  updateProgress(0);
  renderChapter();
}

function updateProgress(charOffset) {
  const book = activeBook();
  if (!book) return;
  const chapter = book.chapters[app.activeChapter];
  const before = book.chapters
    .slice(0, app.activeChapter)
    .reduce((sum, item) => sum + Math.max(item.text.length, 1), 0);
  const total = book.chapters.reduce((sum, item) => sum + Math.max(item.text.length, 1), 0);
  const percent = Math.min(1, Math.max(0, (before + charOffset) / total));
  book.progress = normalizeProgress({
    chapterIndex: app.activeChapter,
    charOffset: Math.max(0, Math.min(charOffset, chapter.text.length)),
    pageIndex: app.activeChapter,
    percent,
    clientUpdatedAt: Date.now(),
    deviceId: app.settings.deviceId,
    revision: book.progress.revision ?? 0,
  });
  clearTimeout(app.progressSaveTimer);
  app.progressSaveTimer = window.setTimeout(persistMeta, 350);
  scheduleProgressSync(2500);
  updateProgressChrome();
  clearTimeout(app.shelfRenderTimer);
  app.shelfRenderTimer = window.setTimeout(renderShelf, 700);
}

function updateProgressChrome() {
  const book = activeBook();
  if (!book) return;
  const percent = normalizeProgress(book.progress).percent;
  elements.readingProgressBar.style.width = `${Math.round(percent * 1000) / 10}%`;
}

function scheduleProgressSync(delay) {
  clearTimeout(app.syncTimer);
  if (!app.settings.token) {
    updateSyncState("local", "仅保存在此设备");
    return;
  }
  app.syncTimer = window.setTimeout(() => syncProgress({ silent: true }), delay);
  updateSyncState("pending", "等待同步");
}

function loadVoices() {
  if (!("speechSynthesis" in window)) return;
  app.voices = speechSynthesis.getVoices();
}

function selectedVoice(text) {
  const wantsChinese = /[\u3400-\u9fff]/u.test(text.slice(0, 100));
  const language = wantsChinese ? "zh" : "en";
  const candidates = app.voices.filter((voice) =>
    voice.lang.toLowerCase().startsWith(language) || voice.lang.startsWith("cmn")
  );
  const pool = candidates.length ? candidates : app.voices;
  const preset = app.settings.voicePreset;
  const explicit = pool.find((voice) => classifyVoiceName(voice.name) === preset);
  const neutral = pool.find((voice) => classifyVoiceName(voice.name) === null);
  const voice = explicit ?? neutral ?? pool[0] ?? null;
  const pitch = voice?.name && classifyVoiceName(voice.name) === preset
    ? 1
    : preset === "male" ? 0.84 : 1.08;
  return { voice, pitch };
}

function toggleSpeech() {
  if (!("speechSynthesis" in window)) {
    toast("当前浏览器不支持朗读，请使用较新的 Chrome、Edge 或 Safari。");
    return;
  }
  if (speechSynthesis.paused && speechSynthesis.speaking) {
    speechSynthesis.resume();
    updateSpeechChrome();
    return;
  }
  if (speechSynthesis.speaking) {
    speechSynthesis.pause();
    updateSpeechChrome();
    return;
  }
  const start = app.currentSentenceIndex >= 0
    ? app.currentSentenceIndex
    : sentenceIndexAt(app.currentSentences, normalizeProgress(activeBook()?.progress).charOffset);
  startSentence(start);
}

function startSentence(index) {
  const book = activeBook();
  if (!book || !app.currentSentences[index]) return;
  if (!("speechSynthesis" in window)) {
    toast("当前浏览器不支持朗读。");
    return;
  }

  app.speechSession += 1;
  const session = app.speechSession;
  speechSynthesis.cancel();
  app.currentSentenceIndex = index;
  app.activeWordStart = -1;
  app.activeWordEnd = -1;
  app.lastScrolledSentence = -1;

  const sentence = app.currentSentences[index];
  const { voice, pitch } = selectedVoice(sentence.text);
  const utterance = new SpeechSynthesisUtterance(sentence.text);
  if (voice) utterance.voice = voice;
  utterance.pitch = pitch;
  utterance.rate = app.settings.speechRate;
  utterance.lang = voice?.lang ?? (/[\u3400-\u9fff]/u.test(sentence.text) ? "zh-CN" : "en-US");

  utterance.onboundary = (event) => {
    if (session !== app.speechSession) return;
    const relativeStart = Math.max(0, event.charIndex || 0);
    const defaultLength = nextWordLength(sentence.text, relativeStart);
    const relativeEnd = relativeStart + (event.charLength || defaultLength);
    app.activeWordStart = sentence.start + relativeStart;
    app.activeWordEnd = sentence.start + Math.max(relativeStart + 1, relativeEnd);
    updateProgress(sentence.start + relativeStart);
    scheduleWordRender();
  };
  utterance.onend = () => {
    if (session !== app.speechSession) return;
    if (index + 1 < app.currentSentences.length) {
      startSentence(index + 1);
      return;
    }
    if (app.activeChapter + 1 < book.chapters.length) {
      app.activeChapter += 1;
      app.currentSentenceIndex = -1;
      updateProgress(0);
      renderChapter();
      startSentence(0);
      return;
    }
    stopSpeech();
    toast("已经读完这一章。");
  };
  utterance.onerror = (event) => {
    if (session !== app.speechSession || ["interrupted", "canceled"].includes(event.error)) return;
    stopSpeech();
    toast(`朗读中断：${event.error}`);
  };

  updateProgress(sentence.start);
  renderChapter();
  speechSynthesis.speak(utterance);
  updateSpeechChrome();
}

function stopSpeech({ render = true } = {}) {
  app.speechSession += 1;
  if (app.wordRenderFrame) {
    cancelAnimationFrame(app.wordRenderFrame);
    app.wordRenderFrame = 0;
  }
  if ("speechSynthesis" in window) speechSynthesis.cancel();
  app.currentSentenceIndex = -1;
  app.activeWordStart = -1;
  app.activeWordEnd = -1;
  app.lastScrolledSentence = -1;
  updateSpeechChrome();
  if (render) renderChapter();
}

function scheduleWordRender() {
  if (app.wordRenderFrame) return;
  app.wordRenderFrame = requestAnimationFrame(() => {
    app.wordRenderFrame = 0;
    renderChapter();
  });
}

function previousSentence() {
  const index = Math.max(0, app.currentSentenceIndex - 1);
  startSentence(index);
}

function nextSentence() {
  const index = Math.min(app.currentSentences.length - 1, app.currentSentenceIndex + 1);
  startSentence(Math.max(0, index));
}

function updateSpeechChrome() {
  const speaking = "speechSynthesis" in window &&
    speechSynthesis.speaking &&
    !speechSynthesis.paused;
  elements.playButton.textContent = speaking ? "Ⅱ" : "▶";
  elements.playButton.setAttribute("aria-label", speaking ? "暂停朗读" : "开始朗读");
  elements.voiceDot.classList.toggle("is-speaking", speaking);
  const sentence = app.currentSentences[app.currentSentenceIndex];
  elements.nowReadingLabel.textContent = sentence
    ? sentence.text.slice(0, 72)
    : "点击句子开始朗读";
  elements.previousSentenceButton.disabled = app.currentSentenceIndex <= 0;
  elements.nextSentenceButton.disabled =
    app.currentSentenceIndex < 0 ||
    app.currentSentenceIndex >= app.currentSentences.length - 1;
}

function cycleSpeed() {
  const currentIndex = speedValues.findIndex(
    (value) => Math.abs(value - app.settings.speechRate) < 0.01,
  );
  app.settings.speechRate = speedValues[(currentIndex + 1) % speedValues.length];
  persistMeta();
  updateSpeedLabel();
  if (app.currentSentenceIndex >= 0) startSentence(app.currentSentenceIndex);
}

function updateSpeedLabel() {
  elements.speedButton.textContent = `${app.settings.speechRate.toFixed(
    app.settings.speechRate === Math.round(app.settings.speechRate) ? 0 : 2,
  )}×`;
}

function cycleFontScale() {
  app.settings.fontScale = (app.settings.fontScale + 1) % fontScales.length;
  persistMeta();
  applyFontScale();
}

function applyFontScale() {
  const scale = fontScales[app.settings.fontScale] ?? 0;
  document.documentElement.style.setProperty("--reader-font-size", `${19 + scale * 2}px`);
  document.documentElement.style.setProperty("--reader-line-height", `${1.92 + scale * 0.1}`);
}

function openSettings() {
  elements.syncEndpointInput.value = app.settings.syncEndpoint;
  elements.syncTokenInput.value = app.settings.token;
  elements.deviceIdInput.value = app.settings.deviceId;
  elements.settingsDialog.showModal();
}

function saveSettingsAndSync() {
  app.settings.syncEndpoint = normalizeEndpoint(elements.syncEndpointInput.value);
  app.settings.token = elements.syncTokenInput.value.trim();
  persistMeta();
  elements.settingsDialog.close();
  updateSyncState();
  syncProgress();
}

function resetDeviceId() {
  app.settings.deviceId = newDeviceId();
  elements.deviceIdInput.value = app.settings.deviceId;
  persistMeta();
}

async function syncProgress({ silent = false } = {}) {
  if (app.syncInFlight) return;
  if (!app.settings.token) {
    updateSyncState("local", "未配置同步");
    if (!silent) openSettings();
    return;
  }

  app.syncInFlight = true;
  updateSyncState("syncing", "正在同步");
  elements.syncButton.disabled = true;
  let remoteChanged = false;

  try {
    for (const book of Object.values(app.books)) {
      const progress = normalizeProgress(book.progress);
      if (!progress.clientUpdatedAt) continue;
      const response = await syncRequest(`/progress/${book.bookKey}`, {
        method: "PUT",
        body: JSON.stringify({
          chapterIndex: progress.chapterIndex,
          charOffset: progress.charOffset,
          pageIndex: progress.pageIndex,
          percent: progress.percent,
          clientUpdatedAt: progress.clientUpdatedAt,
          deviceId: app.settings.deviceId,
          baseRevision: progress.revision ?? 0,
        }),
      });
      if (response.status === 200) {
        const { record } = await response.json();
        book.progress = normalizeProgress(record);
      } else if (response.status === 409) {
        const { record } = await response.json();
        remoteChanged = applyRemoteRecord(record) || remoteChanged;
      } else {
        throw await apiError(response);
      }
    }

    const snapshotResponse = await syncRequest("/progress");
    if (!snapshotResponse.ok) throw await apiError(snapshotResponse);
    const snapshot = await snapshotResponse.json();
    for (const record of snapshot.records ?? []) {
      remoteChanged = applyRemoteRecord(record) || remoteChanged;
    }

    persistMeta();
    updateSyncState("synced", `刚刚同步 · ${snapshot.records?.length ?? 0} 本`);
    elements.syncBadge.textContent = "已同步";
    renderShelf();
    if (remoteChanged && activeBook()) {
      app.activeChapter = Math.min(
        normalizeProgress(activeBook().progress).chapterIndex,
        activeBook().chapters.length - 1,
      );
      renderChapter();
    }
    if (!silent) toast("阅读进度已同步。");
  } catch (error) {
    console.error(error);
    updateSyncState("error", "同步失败");
    if (!silent) toast(`同步失败：${error.message}`);
  } finally {
    app.syncInFlight = false;
    elements.syncButton.disabled = false;
  }
}

function applyRemoteRecord(record) {
  const incoming = normalizeProgress(record);
  const localBook = app.books[incoming.bookKey];
  if (!localBook) {
    const previous = app.remoteProgress[incoming.bookKey];
    if (!previous || incoming.clientUpdatedAt > previous.clientUpdatedAt) {
      app.remoteProgress[incoming.bookKey] = incoming;
      return true;
    }
    return false;
  }
  const local = normalizeProgress(localBook.progress);
  if (incoming.clientUpdatedAt > local.clientUpdatedAt ||
    (incoming.clientUpdatedAt === local.clientUpdatedAt &&
      incoming.deviceId.localeCompare(local.deviceId) > 0)
  ) {
    localBook.progress = incoming;
    return true;
  }
  return false;
}

async function syncRequest(path, options = {}) {
  const endpoint = normalizeEndpoint(app.settings.syncEndpoint);
  return fetch(`${endpoint}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${app.settings.token}`,
      "Content-Type": "application/json",
      ...(options.headers ?? {}),
    },
    cache: "no-store",
  });
}

async function apiError(response) {
  let detail = "";
  try {
    const value = await response.json();
    detail = value.error ?? "";
  } catch {
    // Keep the HTTP status when the server did not return JSON.
  }
  if (response.status === 401) return new Error("同步令牌不正确");
  return new Error(detail || `HTTP ${response.status}`);
}

function updateSyncState(state = null, label = null) {
  if (state) elements.syncState.dataset.state = state;
  if (label) elements.syncState.textContent = label;
  if (!state && !app.settings.token) {
    elements.syncState.dataset.state = "local";
    elements.syncState.textContent = "仅保存在此设备";
    elements.syncBadge.textContent = "本地";
  } else if (!state) {
    elements.syncState.dataset.state = "pending";
    elements.syncState.textContent = "已配置同步";
    elements.syncBadge.textContent = "待同步";
  }
}

async function addSampleBook() {
  const sample = `# 灯塔与纸页

这是一份页声 Web 端的示例文稿。

## 一、清晨

清晨五点，海雾还没有散。灯塔立在很远的地方，像一枚被遗忘的句号。林舟把书摊在窗边，纸页被风轻轻掀起，又安静地落回原处。

他读到一个很长的句子：人们总以为声音是从远处传来的，可有时候，声音只是让眼前的文字忽然有了方向。

## 二、English Notes

The lighthouse was still visible through the fog. It was not a destination, only a reference point that made the coastline easier to understand.

This sentence exercises reading, speech boundaries, and progress sync between devices.

## 三、归途

傍晚，林舟合上书。他把书放回书架，决定下次从“归途”继续。`;
  const bytes = new TextEncoder().encode(sample);
  const bookKey = toHex(await sha256(bytes));
  app.books[bookKey] = {
    bookKey,
    title: "灯塔与纸页",
    format: "text",
    chapters: parseMarkdown(sample, "灯塔与纸页"),
    importedAt: Date.now(),
    lastOpenedAt: Date.now(),
    progress: defaultProgress(),
  };
  persistLibrary();
}

function tokenizeSentences(text) {
  const ranges = [];
  let start = firstVisible(text, 0);
  const hard = new Set(["。", "！", "？", "；", "!", "?", ";"]);
  const soft = new Set(["，", "、", "：", ",", ":", "—"]);
  const closers = new Set(["”", "’", "\"", "'", "）", ")", "】", "]"]);

  while (start < text.length) {
    let cursor = start;
    let boundary = -1;
    while (cursor < text.length) {
      const char = text[cursor];
      if (char === "\n") {
        boundary = cursor + 1;
        break;
      }
      if (hard.has(char)) {
        boundary = includeClosers(text, cursor + 1, closers);
        break;
      }
      if (char === "." &&
        (cursor + 1 >= text.length || /\s/.test(text[cursor + 1]))
      ) {
        boundary = includeClosers(text, cursor + 1, closers);
        break;
      }
      if (cursor - start >= 220 && soft.has(char)) {
        boundary = includeClosers(text, cursor + 1, closers);
        break;
      }
      cursor += 1;
    }
    if (boundary <= start) boundary = text.length;
    let trimmedStart = start;
    let trimmedEnd = boundary;
    while (trimmedStart < trimmedEnd && /\s/.test(text[trimmedStart])) trimmedStart += 1;
    while (trimmedEnd > trimmedStart && /\s/.test(text[trimmedEnd - 1])) trimmedEnd -= 1;
    if (trimmedStart < trimmedEnd && /[\p{L}\p{N}]/u.test(text.slice(trimmedStart, trimmedEnd))) {
      ranges.push({
        start: trimmedStart,
        end: trimmedEnd,
        text: text.slice(trimmedStart, trimmedEnd),
      });
    }
    start = firstVisible(text, boundary);
  }
  return ranges;
}

function sentenceIndexAt(ranges, offset) {
  if (!ranges.length) return 0;
  let result = 0;
  for (let index = 0; index < ranges.length; index += 1) {
    if (ranges[index].start <= offset) result = index;
    else break;
  }
  return result;
}

function nextWordLength(text, start) {
  let end = start;
  while (end < text.length && !/[\s，。！？；、,.!?;:]/u.test(text[end])) end += 1;
  if (end < text.length) end += 1;
  return Math.max(1, end - start);
}

function firstVisible(text, from) {
  let index = from;
  while (index < text.length && /\s/.test(text[index])) index += 1;
  return index;
}

function includeClosers(text, from, closers) {
  let index = from;
  while (index < text.length && closers.has(text[index])) index += 1;
  return index;
}

function classifyVoiceName(name) {
  const value = String(name).toLocaleLowerCase();
  const female = [
    "female", "woman", "girl", "xiaoxiao", "xiaoyi", "xiaomo", "xiaoxuan",
    "xiaohan", "huihui", "yaoyao", "tingting", "ting-ting", "meijia", "mei-jia",
    "samantha", "victoria", "zira", "aria", "jenny", "michelle", "ava",
    "allison", "susan", "karen", "moira", "tessa", "fiona", "nora",
  ];
  const male = [
    "male", "boy", "yunxi", "yunjian", "yunyang", "yunye", "yunfeng",
    "kangkang", "david", "daniel", "alex", "fred", "tom", "george", "james",
    "oliver", "rishi", "ravi", "lee", "li-mu",
  ];
  if (female.some((marker) => value.includes(marker))) return "female";
  if (male.some((marker) => value.includes(marker))) return "male";
  return null;
}

function activeBook() {
  return app.activeBookKey ? app.books[app.activeBookKey] : null;
}

function defaultProgress() {
  return {
    chapterIndex: 0,
    charOffset: 0,
    pageIndex: 0,
    percent: 0,
    clientUpdatedAt: 0,
    serverUpdatedAt: 0,
    revision: 0,
    deviceId: app.settings.deviceId,
  };
}

function normalizeProgress(value) {
  const progress = value?.progress ?? value ?? {};
  return {
    chapterIndex: nonNegativeInteger(progress.chapterIndex),
    charOffset: nonNegativeInteger(progress.charOffset),
    pageIndex: nonNegativeInteger(progress.pageIndex),
    percent: clamp(Number(progress.percent) || 0, 0, 1),
    clientUpdatedAt: nonNegativeInteger(progress.clientUpdatedAt),
    serverUpdatedAt: nonNegativeInteger(progress.serverUpdatedAt),
    revision: nonNegativeInteger(progress.revision),
    deviceId: String(progress.deviceId ?? app.settings.deviceId ?? ""),
    bookKey: progress.bookKey,
  };
}

function nonNegativeInteger(value) {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0;
}

function clamp(value, minimum, maximum) {
  return Math.min(maximum, Math.max(minimum, value));
}

function compareBooks(left, right) {
  return (right.lastOpenedAt ?? 0) - (left.lastOpenedAt ?? 0);
}

function normalizeEndpoint(value) {
  const fallback = defaultEndpoint();
  const raw = String(value ?? "").trim() || fallback;
  try {
    const url = new URL(raw);
    if (url.pathname === "/" || url.pathname === "") url.pathname = "/api/v1";
    return url.toString().replace(/\/+$/, "");
  } catch {
    return raw.replace(/\/+$/, "");
  }
}

function defaultEndpoint() {
  if (location.protocol === "http:" || location.protocol === "https:") {
    return `${location.origin}/api/v1`;
  }
  return "http://127.0.0.1:8787/api/v1";
}

function newDeviceId() {
  const id = crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `web-${id}`;
}

function decodeText(bytes) {
  if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    return new TextDecoder("utf-8").decode(bytes.subarray(3));
  }
  try {
    return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    return new TextDecoder("gb18030").decode(bytes);
  }
}

function node(tag, className = "", text = null) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text !== null) element.textContent = text;
  return element;
}

function toast(message) {
  clearTimeout(app.toastTimer);
  elements.toast.textContent = message;
  elements.toast.classList.add("is-visible");
  app.toastTimer = window.setTimeout(() => {
    elements.toast.classList.remove("is-visible");
  }, 3200);
}

function safeParse(value) {
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function registerServiceWorker() {
  if ("serviceWorker" in navigator && window.isSecureContext) {
    navigator.serviceWorker.register("./sw.js").catch(console.error);
  }
}
