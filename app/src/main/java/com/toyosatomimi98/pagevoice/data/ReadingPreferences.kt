package com.toyosatomimi98.pagevoice.data

import android.content.Context

class ReadingPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "reader_preferences",
        Context.MODE_PRIVATE,
    )

    var fontScale: Float
        get() = preferences.getFloat(KEY_FONT_SCALE, 1f).coerceIn(0.78f, 1.55f)
        set(value) {
            preferences.edit().putFloat(KEY_FONT_SCALE, value.coerceIn(0.78f, 1.55f)).apply()
        }

    var lineSpacing: Float
        get() = preferences.getFloat(KEY_LINE_SPACING, 1.78f).coerceIn(1.45f, 2.15f)
        set(value) {
            preferences.edit().putFloat(KEY_LINE_SPACING, value.coerceIn(1.45f, 2.15f)).apply()
        }

    var speechRate: Float
        get() = preferences.getFloat(KEY_SPEECH_RATE, 0.95f).coerceIn(0.5f, 2f)
        set(value) {
            preferences.edit().putFloat(KEY_SPEECH_RATE, value.coerceIn(0.5f, 2f)).apply()
        }

    var voicePreset: ReaderVoicePreset
        get() = ReaderVoicePreset.fromId(preferences.getString(KEY_VOICE_PRESET, null))
        set(value) {
            preferences.edit().putString(KEY_VOICE_PRESET, value.id).apply()
        }

    private companion object {
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_LINE_SPACING = "line_spacing"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_VOICE_PRESET = "voice_preset"
    }
}
