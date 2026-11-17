package com.toyosatomimi98.pagevoice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceTokenizerTest {
    @Test
    fun `chinese punctuation creates stable source ranges`() {
        val text = "第一句。第二句！“第三句？”"
        val spans = SentenceTokenizer.tokenize(text)

        assertEquals(3, spans.size)
        spans.forEach { span ->
            assertEquals(span.text, text.substring(span.start, span.end))
        }
    }

    @Test
    fun `decimal points do not split an english sentence`() {
        val text = "The value is 3.14 today. It is stable."
        val spans = SentenceTokenizer.tokenize(text)

        assertEquals(2, spans.size)
        assertTrue(spans.first().text.contains("3.14"))
    }

    @Test
    fun `offset resolves to containing sentence`() {
        val spans = SentenceTokenizer.tokenize("A sentence. Another sentence.")

        assertEquals(0, SentenceTokenizer.sentenceIndexAt(spans, 4))
        assertEquals(1, SentenceTokenizer.sentenceIndexAt(spans, 16))
    }
}
