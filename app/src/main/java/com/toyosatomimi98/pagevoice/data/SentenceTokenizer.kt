package com.toyosatomimi98.pagevoice.data

/**
 * Splits prose into stable ranges suitable for TTS. Indices always refer to the
 * original chapter text so the reader can highlight without rebuilding markup.
 */
object SentenceTokenizer {
    private const val SOFT_MAX_LENGTH = 220

    fun tokenize(text: String): List<SentenceSpan> {
        if (text.isBlank()) return emptyList()

        val result = mutableListOf<SentenceSpan>()
        var start = firstVisibleCharacter(text, 0)

        while (start < text.length) {
            var cursor = start
            var boundary = -1

            while (cursor < text.length) {
                val char = text[cursor]
                if (char == '\n' && !isLikelyListContinuation(text, cursor)) {
                    boundary = cursor + 1
                    break
                }

                if (char in HARD_BOUNDARIES) {
                    boundary = includeTrailingClosers(text, cursor + 1)
                    break
                }

                if (char == '.' && isSentencePeriod(text, cursor)) {
                    boundary = includeTrailingClosers(text, cursor + 1)
                    break
                }

                if (cursor - start >= SOFT_MAX_LENGTH && char in SOFT_BOUNDARIES) {
                    boundary = includeTrailingClosers(text, cursor + 1)
                    break
                }
                cursor++
            }

            if (boundary <= start) boundary = text.length
            addTrimmedRange(result, text, start, boundary)
            start = firstVisibleCharacter(text, boundary)
        }

        return result
    }

    fun sentenceIndexAt(spans: List<SentenceSpan>, charOffset: Int): Int {
        if (spans.isEmpty()) return 0
        val clamped = charOffset.coerceAtLeast(0)
        val exact = spans.indexOfLast { clamped >= it.start }
        return exact.coerceAtLeast(0)
    }

    private fun addTrimmedRange(
        output: MutableList<SentenceSpan>,
        source: String,
        rawStart: Int,
        rawEnd: Int,
    ) {
        var start = rawStart
        var end = rawEnd.coerceAtMost(source.length)
        while (start < end && source[start].isWhitespace()) start++
        while (end > start && source[end - 1].isWhitespace()) end--
        if (start >= end) return

        val value = source.substring(start, end)
        if (value.any { it.isLetterOrDigit() }) {
            output += SentenceSpan(start = start, end = end, text = value)
        }
    }

    private fun firstVisibleCharacter(text: String, from: Int): Int {
        var index = from.coerceIn(0, text.length)
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }

    private fun includeTrailingClosers(text: String, from: Int): Int {
        var end = from.coerceAtMost(text.length)
        while (end < text.length && text[end] in CLOSING_MARKS) end++
        return end
    }

    private fun isSentencePeriod(text: String, index: Int): Boolean {
        val previous = text.getOrNull(index - 1)
        val next = text.getOrNull(index + 1)
        if (previous?.isDigit() == true && next?.isDigit() == true) return false
        if (previous == '.' || next == '.') return false
        return next == null || next.isWhitespace() || next.isUpperCase()
    }

    private fun isLikelyListContinuation(text: String, newline: Int): Boolean {
        val nextLine = text.substring(newline + 1).takeWhile { it != '\n' }.trimStart()
        return nextLine.startsWith("- ") ||
            nextLine.startsWith("* ") ||
            nextLine.matches(Regex("""\d+[.)]\s+.*"""))
    }

    private val HARD_BOUNDARIES = setOf('。', '！', '？', '；', '!', '?', ';')
    private val SOFT_BOUNDARIES = setOf('，', '、', '：', ',', ':', '—')
    private val CLOSING_MARKS = setOf('”', '’', '"', '\'', '）', ')', '】', ']')
}
