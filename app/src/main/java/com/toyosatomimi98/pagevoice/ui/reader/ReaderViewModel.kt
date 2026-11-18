package com.toyosatomimi98.pagevoice.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.getWorkInfosForUniqueWorkFlow
import androidx.work.workDataOf
import com.toyosatomimi98.pagevoice.PageVoiceApplication
import com.toyosatomimi98.pagevoice.data.BookContent
import com.toyosatomimi98.pagevoice.data.BookFormat
import com.toyosatomimi98.pagevoice.data.LibraryBook
import com.toyosatomimi98.pagevoice.data.ReadingProgress
import com.toyosatomimi98.pagevoice.data.ReadingPreferences
import com.toyosatomimi98.pagevoice.data.ReaderVoicePreset
import com.toyosatomimi98.pagevoice.data.SentenceSpan
import com.toyosatomimi98.pagevoice.data.SentenceTokenizer
import com.toyosatomimi98.pagevoice.export.AudioExportWorker
import com.toyosatomimi98.pagevoice.tts.ReaderTtsController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: LibraryBook? = null,
    val content: BookContent? = null,
    val currentChapterIndex: Int = 0,
    val charOffset: Int = 0,
    val activeSentence: SentenceSpan? = null,
    val activeWordRange: IntRange? = null,
    val isPlaying: Boolean = false,
    val isTtsReady: Boolean = false,
    val speed: Float = 0.95f,
    val voicePreset: ReaderVoicePreset = ReaderVoicePreset.FEMALE,
    val message: String? = null,
    val fatalError: String? = null,
)

class ReaderViewModel(
    application: Application,
    private val bookId: String,
) : AndroidViewModel(application) {
    private val repository = (application as PageVoiceApplication).bookRepository
    private val preferences = ReadingPreferences(application)
    private val _uiState = MutableStateFlow(
        ReaderUiState(
            speed = preferences.speechRate,
            voicePreset = preferences.voicePreset,
        ),
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val sentenceCache = mutableMapOf<Int, List<SentenceSpan>>()
    private var currentSentenceIndex = -1
    private var playbackChapterIndex = -1
    private var activeUtteranceId: String? = null
    private var utteranceSequence = 0
    private var progressJob: Job? = null
    private var exportObserverJob: Job? = null

    private val tts = ReaderTtsController(
        context = application,
        onReadyChanged = { ready -> _uiState.value = _uiState.value.copy(isTtsReady = ready) },
        onStart = { utteranceId ->
            if (utteranceId == activeUtteranceId) {
                _uiState.value = _uiState.value.copy(isPlaying = true)
            }
        },
        onRange = ::handleTtsRange,
        onDone = ::handleTtsDone,
        onError = ::showMessage,
    )

    init {
        tts.setSpeechRate(_uiState.value.speed)
        loadBook()
    }

    fun onChapterSelected(chapterIndex: Int, charOffset: Int = 0) {
        val content = _uiState.value.content ?: return
        if (chapterIndex !in content.chapters.indices) return
        val wasPlaying = _uiState.value.isPlaying
        tts.stop()
        activeUtteranceId = null
        currentSentenceIndex = -1
        playbackChapterIndex = -1
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = chapterIndex,
            charOffset = charOffset.coerceAtLeast(0),
            activeSentence = null,
            activeWordRange = null,
            isPlaying = false,
        )
        persistProgressSoon()
        if (wasPlaying) {
            val sentence = sentencesFor(chapterIndex).firstOrNull {
                it.end > charOffset
            } ?: return
            speakSentence(
                chapterIndex = chapterIndex,
                sentenceIndex = sentencesFor(chapterIndex).indexOf(sentence),
            )
        }
    }

    fun onPageVisible(chapterIndex: Int, charOffset: Int) {
        val state = _uiState.value
        if (state.isLoading || state.content == null) return
        if (chapterIndex == state.currentChapterIndex && charOffset == state.charOffset) return
        _uiState.value = state.copy(
            currentChapterIndex = chapterIndex.coerceIn(state.content.chapters.indices),
            charOffset = charOffset.coerceAtLeast(0),
        )
        persistProgressSoon()
    }

    fun togglePlayback() {
        val state = _uiState.value
        val content = state.content ?: return
        if (state.isPlaying) {
            tts.stop()
            activeUtteranceId = null
            currentSentenceIndex = -1
            playbackChapterIndex = -1
            _uiState.value = state.copy(
                isPlaying = false,
                activeSentence = null,
                activeWordRange = null,
            )
            return
        }

        val chapterIndex = state.currentChapterIndex.coerceIn(content.chapters.indices)
        val sentences = sentencesFor(chapterIndex)
        if (sentences.isEmpty()) {
            val next = nextNonEmptyChapter(chapterIndex)
            if (next == null) {
                showMessage("这本书没有可供朗读的文字。")
            } else {
                speakSentence(next, 0)
            }
            return
        }

        val sentenceIndex = if (state.activeSentence == null) {
            SentenceTokenizer.sentenceIndexAt(sentences, state.charOffset)
        } else {
            currentSentenceIndex.coerceAtLeast(
                SentenceTokenizer.sentenceIndexAt(sentences, state.charOffset),
            )
        }
        speakSentence(chapterIndex, sentenceIndex.coerceIn(sentences.indices))
    }

    fun previousSentence() {
        val state = _uiState.value
        val content = state.content ?: return
        val chapterIndex = if (state.isPlaying && playbackChapterIndex in content.chapters.indices) {
            playbackChapterIndex
        } else {
            state.currentChapterIndex.coerceIn(content.chapters.indices)
        }
        val sentences = sentencesFor(chapterIndex)
        if (sentences.isEmpty()) {
            previousChapterWithText(chapterIndex)?.let { speakSentence(it, Int.MAX_VALUE) }
            return
        }
        val current = if (currentSentenceIndex in sentences.indices &&
            playbackChapterIndex == chapterIndex
        ) {
            currentSentenceIndex
        } else {
            SentenceTokenizer.sentenceIndexAt(sentences, state.charOffset)
        }
        if (current > 0) {
            speakSentence(chapterIndex, current - 1)
        } else {
            previousChapterWithText(chapterIndex)?.let { speakSentence(it, Int.MAX_VALUE) }
        }
    }

    fun nextSentence() {
        val state = _uiState.value
        val content = state.content ?: return
        val chapterIndex = if (state.isPlaying && playbackChapterIndex in content.chapters.indices) {
            playbackChapterIndex
        } else {
            state.currentChapterIndex.coerceIn(content.chapters.indices)
        }
        val sentences = sentencesFor(chapterIndex)
        if (sentences.isEmpty()) {
            nextNonEmptyChapter(chapterIndex)?.let { speakSentence(it, 0) }
            return
        }
        val current = if (currentSentenceIndex in sentences.indices &&
            playbackChapterIndex == chapterIndex
        ) {
            currentSentenceIndex
        } else {
            SentenceTokenizer.sentenceIndexAt(sentences, state.charOffset)
        }
        if (current < sentences.lastIndex) {
            speakSentence(chapterIndex, current + 1)
        } else {
            nextNonEmptyChapter(chapterIndex)?.let { speakSentence(it, 0) }
        }
    }

    fun startPlaybackAt(charOffset: Int) {
        val state = _uiState.value
        val chapterIndex = state.currentChapterIndex
        val sentences = sentencesFor(chapterIndex)
        if (sentences.isEmpty()) {
            showMessage("当前页没有可朗读文字。")
            return
        }
        val sentenceIndex = SentenceTokenizer.sentenceIndexAt(sentences, charOffset)
        speakSentence(chapterIndex, sentenceIndex)
    }

    fun cycleSpeed() {
        val speedValues = listOf(0.75f, 0.85f, 0.95f, 1.1f, 1.25f, 1.45f, 1.65f)
        val current = _uiState.value.speed
        val index = speedValues.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
            .let { if (it < 0) 2 else it }
        val next = speedValues[(index + 1) % speedValues.size]
        _uiState.value = _uiState.value.copy(speed = next)
        tts.setSpeechRate(next)
        preferences.speechRate = next
    }

    fun setVoicePreset(preset: ReaderVoicePreset) {
        val state = _uiState.value
        if (state.voicePreset == preset) return
        preferences.voicePreset = preset
        _uiState.value = state.copy(voicePreset = preset)

        if (state.isPlaying &&
            playbackChapterIndex >= 0 &&
            currentSentenceIndex >= 0
        ) {
            tts.stop()
            activeUtteranceId = null
            speakSentence(playbackChapterIndex, currentSentenceIndex)
        }
    }

    fun jumpToFraction(fraction: Float) {
        val content = _uiState.value.content ?: return
        val safe = fraction.coerceIn(0f, 1f)
        if (_uiState.value.book?.format == BookFormat.PDF) {
            val chapterIndex = (safe * content.chapters.lastIndex.coerceAtLeast(0)).toInt()
            onChapterSelected(chapterIndex)
            return
        }

        val targetCharacters = (safe * content.totalCharacters).toInt()
        var consumed = 0
        content.chapters.forEachIndexed { index, chapter ->
            val length = chapter.text.length.coerceAtLeast(1)
            if (targetCharacters <= consumed + length || index == content.chapters.lastIndex) {
                onChapterSelected(index, (targetCharacters - consumed).coerceIn(0, chapter.text.length))
                return
            }
            consumed += length
        }
    }

    fun startAudioExport() {
        val state = _uiState.value
        val book = state.book ?: return
        if (state.content?.chapters?.any { it.text.isNotBlank() } != true) {
            showMessage("这本书没有可导出的朗读文字。")
            return
        }
        if (state.isPlaying) {
            tts.stop()
            activeUtteranceId = null
            currentSentenceIndex = -1
            playbackChapterIndex = -1
            _uiState.value = state.copy(
                isPlaying = false,
                activeSentence = null,
                activeWordRange = null,
            )
        }
        val workName = "audio-export-${book.id}"
        val request = OneTimeWorkRequestBuilder<AudioExportWorker>()
            .setInputData(
                workDataOf(
                    AudioExportWorker.KEY_BOOK_ID to book.id,
                    AudioExportWorker.KEY_VOICE_PRESET to state.voicePreset.id,
                ),
            )
            .build()
        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
        _uiState.value = _uiState.value.copy(
            message = "已开始生成整本有声书。导出期间可以继续阅读。",
        )

        exportObserverJob?.cancel()
        exportObserverJob = viewModelScope.launch {
            WorkManager.getInstance(getApplication())
                .getWorkInfosForUniqueWorkFlow(workName)
                .collect { infos ->
                    val info = infos.lastOrNull() ?: return@collect
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            val label = info.outputData.getString(
                                AudioExportWorker.KEY_OUTPUT_LABEL,
                            ) ?: "系统音乐目录"
                            val duration = info.outputData.getLong(
                                AudioExportWorker.KEY_DURATION_MS,
                                0L,
                            )
                            _uiState.value = _uiState.value.copy(
                                message = "有声书已生成：$label${durationLabel(duration)}",
                            )
                        }
                        WorkInfo.State.FAILED -> {
                            val error = info.outputData.getString(
                                AudioExportWorker.KEY_ERROR,
                            ) ?: "音频导出失败。"
                            _uiState.value = _uiState.value.copy(message = error)
                        }
                        else -> Unit
                    }
                }
        }
    }

    fun clearMessage() {
        if (_uiState.value.message != null) {
            _uiState.value = _uiState.value.copy(message = null)
        }
    }

    fun progressFraction(): Float {
        val state = _uiState.value
        val content = state.content ?: return 0f
        if (content.chapters.isEmpty()) return 0f
        if (state.book?.format == BookFormat.PDF) {
            return (state.currentChapterIndex + 1f) / content.chapters.size
        }
        val before = content.chapters.take(state.currentChapterIndex)
            .sumOf { it.text.length.coerceAtLeast(1) }
        val currentLength = content.chapters.getOrNull(state.currentChapterIndex)
            ?.text
            ?.length
            ?.coerceAtLeast(1)
            ?: 1
        return ((before + state.charOffset.coerceAtMost(currentLength)).toFloat() / content.totalCharacters)
            .coerceIn(0f, 1f)
    }

    override fun onCleared() {
        tts.shutdown()
        super.onCleared()
    }

    private fun loadBook() {
        viewModelScope.launch {
            runCatching {
                val book = repository.findBook(bookId)
                    ?: throw IllegalStateException("这本书已从书库移除。")
                val content = repository.loadContent(bookId)
                val saved = book.progress
                repository.touchBook(bookId)
                _uiState.value = ReaderUiState(
                    isLoading = false,
                    book = book,
                    content = content,
                    currentChapterIndex = saved.chapterIndex.coerceIn(content.chapters.indices),
                    charOffset = saved.charOffset.coerceAtLeast(0),
                    isTtsReady = _uiState.value.isTtsReady,
                    speed = preferences.speechRate,
                    voicePreset = preferences.voicePreset,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    fatalError = error.message ?: "书籍打开失败。",
                )
            }
        }
    }

    private fun speakSentence(chapterIndex: Int, sentenceIndex: Int) {
        val content = _uiState.value.content ?: return
        var chapter = chapterIndex
        var sentences = sentencesFor(chapter)
        if (sentences.isEmpty()) {
            chapter = nextNonEmptyChapter(chapter) ?: return
            sentences = sentencesFor(chapter)
        }
        if (sentences.isEmpty()) return

        val safeIndex = if (sentenceIndex == Int.MAX_VALUE) sentences.lastIndex else sentenceIndex
        if (safeIndex !in sentences.indices) return
        currentSentenceIndex = safeIndex
        playbackChapterIndex = chapter
        val sentence = sentences[safeIndex]
        _uiState.value = _uiState.value.copy(
            currentChapterIndex = chapter,
            charOffset = sentence.start,
            activeSentence = sentence,
            activeWordRange = null,
            isPlaying = true,
        )

        utteranceSequence += 1
        activeUtteranceId = "$UTTERANCE_PREFIX$chapter-$safeIndex-$utteranceSequence"
        val utteranceId = checkNotNull(activeUtteranceId)
        val started = tts.speak(
            text = sentence.text,
            utteranceId = utteranceId,
            localeHint = ReaderTtsController.localeFor(
                content.chapters[chapter].text.take(80),
            ),
            voicePreset = _uiState.value.voicePreset,
        )
        if (!started) {
            _uiState.value = _uiState.value.copy(isPlaying = false)
            showMessage("语音引擎尚未就绪，请检查系统文字转语音设置。")
        }
    }

    private fun handleTtsRange(utteranceId: String, start: Int, end: Int) {
        if (utteranceId != activeUtteranceId) return
        val sentence = _uiState.value.activeSentence ?: return
        val wordStart = (sentence.start + start).coerceIn(sentence.start, sentence.end)
        val wordEnd = (sentence.start + end).coerceIn(wordStart, sentence.end)
        if (wordStart < wordEnd) {
            _uiState.value = _uiState.value.copy(activeWordRange = wordStart until wordEnd)
        }
    }

    private fun handleTtsDone(utteranceId: String) {
        if (utteranceId != activeUtteranceId) return
        val state = _uiState.value
        if (!state.isPlaying) return
        val content = state.content ?: return
        val chapterIndex = playbackChapterIndex.coerceIn(0, content.chapters.lastIndex)
        val sentences = sentencesFor(chapterIndex)
        if (currentSentenceIndex + 1 in sentences.indices) {
            speakSentence(chapterIndex, currentSentenceIndex + 1)
            return
        }
        val next = nextNonEmptyChapter(chapterIndex)
        if (next == null) {
            activeUtteranceId = null
            _uiState.value = state.copy(
                isPlaying = false,
                activeWordRange = null,
                message = "已经读完这本书。",
            )
        } else {
            speakSentence(next, 0)
        }
    }

    private fun sentencesFor(chapterIndex: Int): List<SentenceSpan> {
        return sentenceCache.getOrPut(chapterIndex) {
            _uiState.value.content?.chapters?.getOrNull(chapterIndex)
                ?.text
                ?.let(SentenceTokenizer::tokenize)
                .orEmpty()
        }
    }

    private fun nextNonEmptyChapter(from: Int): Int? {
        val chapters = _uiState.value.content?.chapters ?: return null
        return ((from + 1) until chapters.size).firstOrNull { sentencesFor(it).isNotEmpty() }
    }

    private fun previousChapterWithText(from: Int): Int? {
        return (from - 1 downTo 0).firstOrNull { sentencesFor(it).isNotEmpty() }
    }

    private fun persistProgressSoon() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(350)
            val state = _uiState.value
            val now = System.currentTimeMillis()
            repository.persistProgress(
                bookId = bookId,
                progress = ReadingProgress(
                    chapterIndex = state.currentChapterIndex,
                    charOffset = state.charOffset,
                    pageIndex = state.currentChapterIndex,
                    percent = progressFraction(),
                    updatedAt = now,
                ),
            )
        }
    }

    private fun showMessage(message: String) {
        tts.stop()
        activeUtteranceId = null
        playbackChapterIndex = -1
        _uiState.value = _uiState.value.copy(
            message = message,
            isPlaying = false,
            activeWordRange = null,
        )
    }

    private fun durationLabel(durationMillis: Long): String {
        if (durationMillis <= 0) return ""
        val totalSeconds = durationMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "，时长约 $minutes 分 ${seconds.toString().padStart(2, '0')} 秒"
    }

    companion object {
        private const val UTTERANCE_PREFIX = "pagevoice-sentence-"

        fun factory(application: Application, bookId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ReaderViewModel(application, bookId) as T
                }
            }
    }
}
