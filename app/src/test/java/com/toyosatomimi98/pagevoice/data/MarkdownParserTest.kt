package com.toyosatomimi98.pagevoice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class MarkdownParserTest {
    @Test
    fun `headings become chapters and toc entries`() {
        val source = """
            # 山川笔记

            作者：林舟

            山风从纸页之间穿过。

            ## 第一章 出发

            清晨五点，我们向山里走。

            ### 岔路

            **不要** 回头。
        """.trimIndent()

        val parsed = MarkdownParser.parse(source, fallbackTitle = "fallback")

        assertEquals("山川笔记", parsed.title)
        assertEquals("林舟", parsed.author)
        assertEquals(3, parsed.content.chapters.size)
        assertEquals(3, parsed.content.toc.size)
        assertEquals("岔路", parsed.content.toc.last().title)
        assertTrue(parsed.content.chapters.last().text.contains("不要 回头。"))
    }

    @Test
    fun `plain text without heading remains readable`() {
        val parsed = MarkdownParser.parse(
            source = "第一句。\n\n第二句。",
            fallbackTitle = "无标题",
        )

        assertEquals(1, parsed.content.chapters.size)
        assertEquals("前言", parsed.content.chapters.single().title)
    }

    @Test
    fun `gb18030 fallback keeps chinese text`() {
        val expected = "中文编码测试"
        val bytes = expected.toByteArray(Charset.forName("GB18030"))

        assertEquals(expected, MarkdownParser.decode(bytes))
    }
}
