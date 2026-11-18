package com.toyosatomimi98.pagevoice.tts

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.toyosatomimi98.pagevoice.data.ReaderVoicePreset
import com.toyosatomimi98.pagevoice.data.VoiceNameClassifier
import java.util.Locale

object TtsVoiceSelector {
    data class Selection(
        val voiceName: String?,
        val explicitGenderMatch: Boolean,
        val pitch: Float,
    )

    /**
     * Prefer an installed local voice whose name clearly matches the preset.
     * If the engine exposes only a neutral voice, pitch still provides a stable
     * male/female distinction without requiring a third-party TTS service.
     */
    fun apply(
        tts: TextToSpeech,
        locale: Locale,
        preset: ReaderVoicePreset,
    ): Selection {
        val available = runCatching { tts.voices }
            .getOrNull()
            .orEmpty()
            .asSequence()
            .filter { it.locale.language.equals(locale.language, ignoreCase = true) }
            .filterNot { it.isNetworkConnectionRequired }
            .toList()

        val exactLocale = available.filter {
            it.locale.country.equals(locale.country, ignoreCase = true)
        }
        val candidates = (exactLocale.ifEmpty { available }).sortedWith(
            compareByDescending<Voice> { it.quality }
                .thenByDescending { it.locale.country == locale.country }
                .thenBy { it.name },
        )
        val genderMatch = candidates.firstOrNull {
            VoiceNameClassifier.detect(it.name) == preset.gender
        }
        val neutral = candidates.firstOrNull {
            VoiceNameClassifier.detect(it.name) == null
        }
        val selected = genderMatch ?: neutral ?: candidates.firstOrNull()
        if (selected != null) {
            runCatching { tts.setVoice(selected) }
        }

        val explicitMatch = selected != null &&
            VoiceNameClassifier.detect(selected.name) == preset.gender
        val pitch = if (explicitMatch) 1f else preset.fallbackPitch
        tts.setPitch(pitch)
        return Selection(
            voiceName = selected?.name,
            explicitGenderMatch = explicitMatch,
            pitch = pitch,
        )
    }
}
