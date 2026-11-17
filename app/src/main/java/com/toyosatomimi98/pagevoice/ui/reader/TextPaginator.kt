package com.toyosatomimi98.pagevoice.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.toyosatomimi98.pagevoice.data.BookContent
import kotlin.math.max
import kotlin.math.min

data class ReaderPageSlice(
    val chapterIndex: Int,
    val chapterTitle: String,
    val start: Int,
    val end: Int,
)

object TextPaginator {
    fun paginate(
        content: BookContent,
        measurer: TextMeasurer,
        style: TextStyle,
        widthPx: Int,
        maxLines: Int,
    ): List<ReaderPageSlice> {
        if (widthPx <= 0 || maxLines <= 0) return emptyList()
        val pages = mutableListOf<ReaderPageSlice>()
        val constraints = Constraints(maxWidth = widthPx)

        content.chapters.forEachIndexed { chapterIndex, chapter ->
            val source = chapter.text
            if (source.isBlank()) {
                pages += ReaderPageSlice(chapterIndex, chapter.title, 0, 0)
                return@forEachIndexed
            }

            var start = 0
            while (start < source.length) {
                while (start < source.length && source[start].isWhitespace()) start++
                if (start >= source.length) break

                val windowEnd = min(source.length, start + max(1000, maxLines * 100))
                val window = source.substring(start, windowEnd)
                val layout = measurer.measure(
                    text = AnnotatedString(window),
                    style = style,
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                    maxLines = maxLines,
                    constraints = constraints,
                )
                var consumed = if (layout.lineCount > 0) {
                    layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
                } else {
                    1
                }
                consumed = consumed.coerceAtLeast(1).coerceAtMost(window.length)
                var end = start + consumed

                // Prefer a page break at a paragraph boundary when one is very
                // close, then fall back to the exact measured line boundary.
                val paragraphBreak = source.lastIndexOf("\n\n", startIndex = end - 1)
                if (paragraphBreak > start + consumed / 3) end = paragraphBreak + 2

                while (end > start && source[end - 1].isWhitespace()) end--
                if (end <= start) end = min(source.length, start + 1)
                pages += ReaderPageSlice(chapterIndex, chapter.title, start, end)
                start = end
            }
        }
        return pages
    }
}
