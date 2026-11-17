package com.toyosatomimi98.pagevoice.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.toyosatomimi98.pagevoice.PageVoiceApplication
import com.toyosatomimi98.pagevoice.R
import com.toyosatomimi98.pagevoice.data.SentenceTokenizer
import com.toyosatomimi98.pagevoice.tts.ReaderTtsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AudioExportWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val repository = (applicationContext as PageVoiceApplication).bookRepository

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val book = repository.findBook(bookId) ?: return Result.failure(
            Data.Builder().putString(KEY_ERROR, "找不到这本书。").build(),
        )
        val content = runCatching { repository.loadContent(bookId) }.getOrElse {
            return Result.failure(
                Data.Builder().putString(KEY_ERROR, it.message ?: "读取正文失败。").build(),
            )
        }

        setForeground(createForegroundInfo(0))
        val engine = runCatching { initializeTts() }.getOrElse { error ->
            return failure(error.message ?: "系统语音引擎初始化失败。")
        }

        val temporaryDirectory = File(applicationContext.cacheDir, "audio-export-${UUID.randomUUID()}")
            .apply { mkdirs() }
        val chunks = buildChunks(content.chapters.flatMap {
            SentenceTokenizer.tokenize(it.text).map { sentence -> sentence.text }
        })
        if (chunks.isEmpty()) {
            engine.shutdown()
            temporaryDirectory.deleteRecursively()
            return failure("这本书没有可朗读的文字。扫描版 PDF 需要先进行 OCR。")
        }

        val audioFiles = mutableListOf<File>()
        val metadata = mutableListOf<WavInfo>()
        var destination: Destination? = null
        try {
            chunks.forEachIndexed { index, chunk ->
                if (index % 3 == 0) {
                    setForeground(createForegroundInfo((index * 100) / chunks.size))
                }
                val language = ReaderTtsController.localeFor(chunk.take(120))
                val languageStatus = withContext(Dispatchers.Main) { engine.setLanguage(language) }
                if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                    languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    throw IllegalStateException("当前语音引擎缺少${language.displayName}语音包。")
                }

                val file = File(temporaryDirectory, "segment-${index.toString().padStart(5, '0')}.wav")
                synthesize(engine, chunk, file, "pagevoice-export-$index")
                metadata += WavMerger.inspect(file)
                audioFiles += file
            }

            val displayName = safeFileName("${book.title} · 页声朗读.wav")
            val createdDestination = createDestination(displayName)
            destination = createdDestination
            openDestinationStream(createdDestination).use { output ->
                WavMerger.merge(audioFiles, output)
            }
            createdDestination.complete()
            val duration = metadata.sumOf { it.durationMillis }
            val data = Data.Builder()
                .putString(KEY_OUTPUT_URI, createdDestination.uri.toString())
                .putString(KEY_DISPLAY_NAME, displayName)
                .putString(KEY_OUTPUT_LABEL, createdDestination.label)
                .putLong(KEY_DURATION_MS, duration)
                .build()
            showCompletionNotification(createdDestination.label)
            return Result.success(data)
        } catch (error: Exception) {
            destination?.abort()
            return failure(error.message ?: "音频导出失败。")
        } finally {
            engine.stop()
            engine.shutdown()
            temporaryDirectory.deleteRecursively()
        }
    }

    private suspend fun initializeTts(): TextToSpeech {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                lateinit var tts: TextToSpeech
                tts = TextToSpeech(applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        continuation.resume(tts)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException("系统语音引擎初始化失败。"),
                        )
                    }
                }
                continuation.invokeOnCancellation { tts.stop() }
            }
        }
    }

    private suspend fun synthesize(
        engine: TextToSpeech,
        text: String,
        outputFile: File,
        utteranceId: String,
    ) = withContext(Dispatchers.Main) {
        withTimeout(120_000) {
            suspendCancellableCoroutine { continuation ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit

                    override fun onDone(id: String?) {
                        if (id == utteranceId && continuation.isActive) continuation.resume(Unit)
                    }

                    @Deprecated("Required compatibility callback.")
                    override fun onError(id: String?) {
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("语音合成中断。"),
                            )
                        }
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId && continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("语音合成失败（错误 $errorCode）。"),
                            )
                        }
                    }
                })
                val result = engine.synthesizeToFile(
                    text,
                    Bundle().apply {
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, 3)
                    },
                    outputFile,
                    utteranceId,
                )
                if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException("系统语音引擎未能创建音频文件。"),
                    )
                }
                continuation.invokeOnCancellation { engine.stop() }
            }
        }
    }

    private fun createDestination(displayName: String): Destination {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/页声")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = requireNotNull(
                applicationContext.contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    values,
                ),
            ) { "系统没有提供可写入的音频位置。" }
            val pending = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            return Destination(
                uri = uri,
                label = "音乐/页声/$displayName",
                complete = {
                    applicationContext.contentResolver.update(uri, pending, null, null)
                },
                abort = {
                    runCatching { applicationContext.contentResolver.delete(uri, null, null) }
                },
            )
        }

        val directory = File(
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            "页声",
        ).apply { mkdirs() }
        val file = File(directory, displayName)
        return Destination(
            uri = Uri.fromFile(file),
            label = file.absolutePath,
            complete = {},
            abort = { file.delete() },
        )
    }

    private fun buildChunks(sentences: List<String>): List<String> {
        val chunks = mutableListOf<String>()
        val builder = StringBuilder()
        sentences.forEach { raw ->
            val sentence = raw.trim()
            if (sentence.isEmpty()) return@forEach
            if (builder.isNotEmpty() && builder.length + sentence.length > MAX_TTS_CHUNK) {
                chunks += builder.toString()
                builder.setLength(0)
            }
            builder.append(sentence)
            if (builder.lastOrNull() !in CHINESE_PUNCTUATION) builder.append(' ')
        }
        if (builder.isNotBlank()) chunks += builder.toString()
        return chunks
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.notification_export_title))
            .setContentText(if (progress <= 0) "正在准备语音引擎…" else "已完成 $progress%")
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(label: String) {
        ensureNotificationChannel()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID + 1,
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("有声书已生成")
                .setContentText(label)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    applicationContext.getString(R.string.notification_channel_export),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = applicationContext.getString(
                        R.string.notification_channel_export_description,
                    )
                },
            )
        }
    }

    private fun failure(message: String): Result {
        return Result.failure(Data.Builder().putString(KEY_ERROR, message).build())
    }

    private fun safeFileName(value: String): String {
        val cleaned = value.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").trim()
        val base = cleaned.removeSuffix(".wav").trim().take(108)
        return "${base.ifBlank { "页声朗读" }}.wav"
    }

    private data class Destination(
        val uri: Uri,
        val label: String,
        val complete: () -> Unit,
        val abort: () -> Unit,
    )

    private fun openDestinationStream(destination: Destination) = when (destination.uri.scheme) {
        "file" -> requireNotNull(destination.uri.path).let(::File).outputStream()
        else -> requireNotNull(
            applicationContext.contentResolver.openOutputStream(destination.uri),
        )
    }

    companion object {
        const val KEY_BOOK_ID = "book_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_TOTAL = "total"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_OUTPUT_LABEL = "output_label"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_ERROR = "error"
        private const val NOTIFICATION_CHANNEL = "pagevoice_export"
        private const val NOTIFICATION_ID = 4101
        private const val MAX_TTS_CHUNK = 2800
        private val CHINESE_PUNCTUATION = setOf('。', '！', '？', '；', '，', '、')
    }
}
