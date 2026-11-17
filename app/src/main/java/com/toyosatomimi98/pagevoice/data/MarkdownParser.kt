package com.toyosatomimi98.pagevoice.data

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class ParsedMarkdown(
    val title: String?,
    val author: String?,
    val content: BookContent,
)

object MarkdownParser {
    private val headingPattern = Regex("""^(#{1,6})\s+(.+?)\s*#*$""")
    private val authorPattern = Regex("""(?im)^(?:author|作者)\s*[:：]\s*(.+)$""")

    fun parse(source: String, fallbackTitle: String): ParsedMarkdown {
        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val detectedTitle = normalized.lineSequence()
            .mapNotNull { headingPattern.matchEntire(it.trim()) }
            .firstOrNull { it.groupValues[1].length == 1 }
            ?.groupValues
            ?.get(2)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val author = authorPattern.find(normalized)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

        val blocks = mutableListOf<ChapterBlock>()
        var currentTitle = "前言"
        var currentDepth = 0
        var currentBody = StringBuilder()

        fun flush() {
            val cleaned = cleanMarkdownBody(currentBody.toString()).trim()
            if (cleaned.isNotEmpty()) {
                blocks += ChapterBlock(
                    title = currentTitle,
                    depth = currentDepth,
                    text = cleaned,
                )
            }
            currentBody = StringBuilder()
        }

        normalized.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            val heading = headingPattern.matchEntire(line.trim())
            if (heading != null) {
                flush()
                currentTitle = cleanInline(heading.groupValues[2])
                currentDepth = heading.groupValues[1].length - 1
            } else {
                currentBody.appendLine(line)
            }
        }
        flush()

        if (blocks.isEmpty()) {
            throw IllegalArgumentException("这份文档没有可读取的文字。")
        }

        val chapters = blocks.mapIndexed { index, block ->
            BookChapter(
                id = "text-chapter-$index",
                title = block.title.ifBlank { "第 ${index + 1} 章" },
                text = block.text,
            )
        }
        val toc = blocks.mapIndexed { index, block ->
            TocEntry(
                title = block.title.ifBlank { "第 ${index + 1} 章" },
                chapterIndex = index,
                depth = block.depth,
            )
        }

        return ParsedMarkdown(
            title = detectedTitle,
            author = author,
            content = BookContent(
                detectedTitle = detectedTitle ?: fallbackTitle,
                detectedAuthor = author,
                chapters = chapters,
                toc = toc,
            ),
        )
    }

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0].toInt() and 0xFF == 0xEF &&
            bytes[1].toInt() and 0xFF == 0xBB &&
            bytes[2].toInt() and 0xFF == 0xBF
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }

        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            String(bytes, charset("GB18030"))
        }
    }

    private fun cleanMarkdownBody(body: String): String {
        var inCodeFence = false
        return body.lineSequence()
            .mapNotNull { rawLine ->
                val trimmed = rawLine.trimStart()
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    inCodeFence = !inCodeFence
                    return@mapNotNull null
                }
                if (inCodeFence) return@mapNotNull rawLine

                when {
                    trimmed.startsWith(">") -> "  ${cleanInline(trimmed.removePrefix(">").trim())}"
                    trimmed.matches(Regex("""^[-*+]\s+.*""")) ->
                        "• ${cleanInline(trimmed.drop(2))}"
                    trimmed.matches(Regex("""^\d+[.)]\s+.*""")) ->
                        cleanInline(trimmed)
                    trimmed.startsWith("|") ->
                        trimmed.trim('|')
                            .split('|')
                            .joinToString("  ") { it.trim() }
                            .trim()
                    else -> cleanInline(rawLine)
                }
            }
            .joinToString("\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
    }

    private fun cleanInline(input: String): String {
        var value = input
        value = value.replace(Regex("""!\[([^\]]*)]\([^)]*\)"""), "\$1")
        value = value.replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "\$1")
        value = value.replace(Regex("""<https?://[^>]+>"""), "")
        value = value.replace(Regex("""<[^>]+>"""), "")
        value = value.replace("**", "")
        value = value.replace("__", "")
        value = value.replace("*", "")
        value = value.replace("~~", "")
        value = value.replace("`", "")
        return value.trimEnd()
    }

    private data class ChapterBlock(
        val title: String,
        val depth: Int,
        val text: String,
    )
}
