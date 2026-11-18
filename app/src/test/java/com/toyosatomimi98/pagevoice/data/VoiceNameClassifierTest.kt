package com.toyosatomimi98.pagevoice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceNameClassifierTest {
    @Test
    fun `common female voice names are detected`() {
        assertEquals(VoiceGender.FEMALE, VoiceNameClassifier.detect("zh-cn-xiaoxiao-local"))
        assertEquals(VoiceGender.FEMALE, VoiceNameClassifier.detect("en-us-Samantha"))
    }

    @Test
    fun `common male voice names are detected`() {
        assertEquals(VoiceGender.MALE, VoiceNameClassifier.detect("zh-cn-yunxi-local"))
        assertEquals(VoiceGender.MALE, VoiceNameClassifier.detect("en-us-David"))
    }

    @Test
    fun `unknown engine names stay neutral`() {
        assertNull(VoiceNameClassifier.detect("voice-id-7f3a"))
    }

    @Test
    fun `female is not mistaken for the male marker`() {
        assertEquals(VoiceGender.FEMALE, VoiceNameClassifier.detect("engine-female-01"))
    }

    @Test
    fun `voice preset defaults to female and accepts persisted ids`() {
        assertEquals(ReaderVoicePreset.FEMALE, ReaderVoicePreset.fromId(null))
        assertEquals(ReaderVoicePreset.MALE, ReaderVoicePreset.fromId("male"))
    }
}
