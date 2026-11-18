package com.toyosatomimi98.pagevoice.data

enum class ReaderVoicePreset(
    val id: String,
    val label: String,
    val description: String,
    val fallbackPitch: Float,
) {
    FEMALE(
        id = "female",
        label = "女声",
        description = "明亮、清晰，适合长文阅读",
        fallbackPitch = 1.08f,
    ),
    MALE(
        id = "male",
        label = "男声",
        description = "低沉、平稳，适合长文阅读",
        fallbackPitch = 0.84f,
    ),
    ;

    val gender: VoiceGender
        get() = when (this) {
            FEMALE -> VoiceGender.FEMALE
            MALE -> VoiceGender.MALE
        }

    companion object {
        fun fromId(value: String?): ReaderVoicePreset {
            return entries.firstOrNull { it.id == value } ?: FEMALE
        }
    }
}

enum class VoiceGender {
    FEMALE,
    MALE,
}

/**
 * Android does not expose a standard gender field for TTS voices. This
 * classifier only interprets common local voice names; unknown names remain
 * neutral and are differentiated with the preset pitch instead.
 */
object VoiceNameClassifier {
    fun detect(voiceName: String): VoiceGender? {
        val normalized = voiceName.lowercase()
        if (FEMALE_MARKERS.any(normalized::contains)) return VoiceGender.FEMALE
        if (MALE_MARKERS.any(normalized::contains)) return VoiceGender.MALE
        return null
    }

    private val FEMALE_MARKERS = listOf(
        "female",
        "woman",
        "girl",
        "xiaoxiao",
        "xiaoyi",
        "xiaomo",
        "xiaoxuan",
        "xiaohan",
        "huihui",
        "yaoyao",
        "tingting",
        "ting-ting",
        "meijia",
        "mei-jia",
        "samantha",
        "victoria",
        "zira",
        "aria",
        "jenny",
        "michelle",
        "ava",
        "allison",
        "susan",
        "karen",
        "moira",
        "tessa",
        "fiona",
        "nora",
        "sin-ji",
    )

    private val MALE_MARKERS = listOf(
        "male",
        "boy",
        "yunxi",
        "yunjian",
        "yunyang",
        "yunye",
        "yunfeng",
        "kangkang",
        "david",
        "daniel",
        "alex",
        "fred",
        "tom",
        "george",
        "james",
        "oliver",
        "rishi",
        "ravi",
        "lee",
        "li-mu",
    )
}
