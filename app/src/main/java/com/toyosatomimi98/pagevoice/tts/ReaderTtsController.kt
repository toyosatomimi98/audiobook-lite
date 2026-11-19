package com.toyosatomimi98.pagevoice.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.toyosatomimi98.pagevoice.data.ReaderVoicePreset
import java.util.Locale

class ReaderTtsController(
    context: Context,
    private val onReadyChanged: (Boolean) -> Unit,
    private val onStart: (utteranceId: String) -> Unit,
    private val onRange: (utteranceId: String, start: Int, end: Int) -> Unit,
    private val onDone: (utteranceId: String) -> Unit,
    private val onError: (message: String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var isReady = false
    private var speechRate = 0.95f

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS) {
                    configureEngine()
                    isReady = true
                    onReadyChanged(true)
                } else {
                    isReady = false
                    onReadyChanged(false)
                    onError("系统语音引擎初始化失败。请在系统设置中安装或启用文字转语音服务。")
                }
            }
        }
    }

    fun speak(
        text: String,
        utteranceId: String,
        localeHint: Locale,
        voicePreset: ReaderVoicePreset,
    ): Boolean {
        if (!isReady) return false
        val tts = engine ?: return false
        val languageResult = tts.setLanguage(localeHint)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            onError("当前语音引擎不支持这本书的语言。可在系统“文字转语音”设置中安装对应语音包。")
            return false
        }
        TtsVoiceSelector.apply(tts, localeHint, voicePreset)
        tts.setSpeechRate(speechRate)
        val mode = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        return mode == TextToSpeech.SUCCESS
    }

    fun stop() {
        engine?.stop()
    }

    fun setSpeechRate(value: Float) {
        speechRate = value.coerceIn(0.5f, 2f)
        engine?.setSpeechRate(speechRate)
    }

    fun shutdown() {
        isReady = false
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    private fun configureEngine() {
        val tts = engine ?: return
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == null) return
                mainHandler.post { onStart(utteranceId) }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == null) return
                mainHandler.post { onDone(utteranceId) }
            }

            @Deprecated("Deprecated by Android, retained because some older TTS engines only call this callback.")
            override fun onError(utteranceId: String?) {
                mainHandler.post { onError("朗读中断，请检查系统语音引擎后重试。") }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { onError("朗读中断（语音引擎错误 $errorCode）。") }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId == null || start < 0 || end < start) return
                mainHandler.post { onRange(utteranceId, start, end) }
            }
        })
        tts.setSpeechRate(speechRate)
    }

    companion object {
        fun localeFor(text: String): Locale {
            val hasCjk = text.any { character ->
                character.code in 0x4E00..0x9FFF ||
                    character.code in 0x3400..0x4DBF
            }
            return if (hasCjk) Locale.SIMPLIFIED_CHINESE else Locale.US
        }
    }
}
